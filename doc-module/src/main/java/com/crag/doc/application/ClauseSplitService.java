package com.crag.doc.application;

import com.crag.doc.application.port.ClauseTypeFallbackPort;
import com.crag.doc.domain.model.ContractChunkRecord;
import com.crag.common.text.ParsedClause;
import com.crag.common.text.TypeSourceEnum;
import com.crag.common.text.ClauseSplitter;
import com.crag.common.text.TypeClassification;
import com.crag.common.domain.SnowflakeIdGenerator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 切分用例：把解析文本切成检索单元并补齐模型兜底标签（规范 5.3）。
 *
 * <p>关键词未命中的条款先落到“其他 + DEFAULT”，再交由 {@link ClauseTypeFallbackPort} 兜底；
 * 兜底端口默认不调用模型，接入 agent-module 后替换为模型分类。</p>
 *
 * <p>为什么切分算法在 common 而不是 doc：规范 5.4 要求知识库与合同用同一份切分实现，而 2.2 不允许 kb → doc
 * 依赖，只能把内核下沉到 common（ADR 0003）。本类只负责“用例编排 + 兜底”，不复制切分算法。</p>
 */
@Service
public class ClauseSplitService {

    private final ClauseSplitter splitter;
    private final ClauseTypeFallbackPort fallbackPort;
    private final SnowflakeIdGenerator idGenerator;

    /**
     * 构造服务。
     *
     * @param splitter     切分器
     * @param fallbackPort 模型兜底分类端口
     * @param idGenerator  雪花标识
     */
    public ClauseSplitService(ClauseSplitter splitter,
                              ClauseTypeFallbackPort fallbackPort,
                              SnowflakeIdGenerator idGenerator) {
        this.splitter = splitter;
        this.fallbackPort = fallbackPort;
        this.idGenerator = idGenerator;
    }

    /**
     * 切分并补齐标签。
     *
     * @param text 解析文本
     * @return 切分结果
     */
    public List<ParsedClause> split(String text) {
        List<ParsedClause> clauses = splitter.split(text);
        List<ParsedClause> resolved = new ArrayList<>(clauses.size());
        for (ParsedClause clause : clauses) {
            if (clause.typeSource() == TypeSourceEnum.DEFAULT) {
                String headline = firstLine(clause.text());
                TypeClassification fallback = fallbackPort.classify(headline, clause.text());
                if (fallback != null && fallback.clauseType() != clause.clauseType()) {
                    resolved.add(withType(clause, fallback));
                    continue;
                }
            }
            resolved.add(clause);
        }
        return List.copyOf(resolved);
    }

    /**
     * 转为切片记录。
     *
     * @param tenantId   租户
     * @param contractId 合同标识
     * @param clauses    切分结果
     * @return 切片记录
     */
    public List<ContractChunkRecord> toRecords(long tenantId, long contractId, List<ParsedClause> clauses) {
        List<ContractChunkRecord> records = new ArrayList<>(clauses.size());
        for (ParsedClause clause : clauses) {
            records.add(ContractChunkRecord.from(idGenerator.nextId(), contractId, tenantId, clause));
        }
        return List.copyOf(records);
    }

    private static ParsedClause withType(ParsedClause clause, TypeClassification classification) {
        return new ParsedClause(clause.clauseNo(), classification.clauseType(), classification.typeSource(),
                clause.text(), clause.fullTextIndex(), clause.charStart(), clause.charEnd(),
                clause.blockIndex(), clause.partIndex(), clause.partCount());
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }
}
