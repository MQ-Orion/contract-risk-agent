package com.crag.doc.application;

import com.crag.doc.application.port.ContractChunkRepository;
import com.crag.doc.application.port.ContractRepository;
import com.crag.doc.application.port.ParseTaskRepository;
import com.crag.doc.domain.model.ContractChunkRecord;
import com.crag.doc.domain.model.ParseStatusEnum;
import com.crag.doc.domain.model.ParseTaskRecord;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 解析进度写入器：只包裹数据库操作，事务边界固定在应用层（规范 3.3、3.8）。
 *
 * <p>解析、模型与文件 IO 一律在事务外执行；本类方法短小，只写库，单事务不超过三张表。</p>
 */
@Service
public class ParseProgressWriter {

    private final ParseTaskRepository taskRepository;
    private final ContractRepository contractRepository;
    private final ContractChunkRepository chunkRepository;

    /**
     * 构造写入器。
     *
     * @param taskRepository     任务仓储
     * @param contractRepository 合同仓储
     * @param chunkRepository    切片仓储
     */
    public ParseProgressWriter(ParseTaskRepository taskRepository,
                               ContractRepository contractRepository,
                               ContractChunkRepository chunkRepository) {
        this.taskRepository = taskRepository;
        this.contractRepository = contractRepository;
        this.chunkRepository = chunkRepository;
    }

    /**
     * 推进任务状态。
     *
     * <p>非法边在 {@code moveTo} 内即抛 40001，不写库；因此调用方不需要也不允许自己判断状态。</p>
     *
     * @param task   任务
     * @param target 目标状态
     * @param now    当前时间（统一时钟注入）
     * @return 更新后的任务
     */
    @Transactional
    public ParseTaskRecord advance(ParseTaskRecord task, ParseStatusEnum target, Instant now) {
        ParseTaskRecord moved = task.moveTo(target, now);
        taskRepository.update(moved);
        return moved;
    }

    /**
     * 落库切片并置为就绪。
     *
     * <p>为什么合同切片可以“整篇替换”而知识库切片不行：合同切片不存 embedding、不参与向量召回（规范 4.2），
     * 替换不产生重复嵌入成本；知识库切片必须按 chunk_key + content_hash 条件 upsert（规范 4.3），两者不可混用。</p>
     *
     * <p>本事务恰好写三张表（切片、合同页数、任务状态），是规范 3.8 允许的上限；再多一张就必须拆分。</p>
     *
     * @param task      任务
     * @param chunks    切片
     * @param pageCount 页数
     * @param now       当前时间
     * @return 更新后的任务
     */
    @Transactional
    public ParseTaskRecord persistChunksAndReady(ParseTaskRecord task, List<ContractChunkRecord> chunks,
                                                 int pageCount, Instant now) {
        // 解析期间合同可能被删除：删除是逻辑删除，此时再写切片会留下“合同不可见但切片仍在”的孤儿数据
        if (contractRepository.findById(task.tenantId(), task.contractId()).isEmpty()) {
            ParseTaskRecord failed = task.fail("CONTRACT_DELETED", "合同已删除，终止解析", now, now);
            taskRepository.update(failed);
            return failed;
        }
        chunkRepository.replaceAll(task.tenantId(), task.contractId(), chunks);
        if (pageCount > 0) {
            contractRepository.updatePageCount(task.tenantId(), task.contractId(), pageCount);
        }
        ParseTaskRecord ready = task.moveTo(ParseStatusEnum.READY, now);
        taskRepository.update(ready);
        return ready;
    }

    /**
     * 记录失败。
     *
     * @param task         任务
     * @param errorCode    错误码名称
     * @param errorMessage 失败原因
     * @param nextRetryAt  下次可重试时间，可为 null
     * @param now          当前时间
     * @return 更新后的任务
     */
    @Transactional
    public ParseTaskRecord fail(ParseTaskRecord task, String errorCode, String errorMessage,
                                Instant nextRetryAt, Instant now) {
        ParseTaskRecord failed = task.fail(errorCode, errorMessage, now, nextRetryAt);
        taskRepository.update(failed);
        return failed;
    }
}
