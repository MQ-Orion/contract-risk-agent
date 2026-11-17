package com.crag.kb.infrastructure.persistence.mapper;

import com.crag.kb.infrastructure.persistence.row.KbChunkSearchRow;
import com.crag.kb.infrastructure.persistence.row.KbChunkUpsertRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 知识库切片 Mapper：写入走条件 upsert，召回 SQL 只在此处出现（规范 4.3、6.2）。
 *
 * <p>SQL 在 {@code resources/mapper/KbChunkMapper.xml}：含 pgvector 操作符、ON CONFLICT 与动态条件，
 * 注解形式会被 IDE 的 SQL 检查误判（{@code <script>}/{@code #{}}/{@code <=>}），XML 才是动态 SQL 的原生载体；
 * 接口层只保留参数契约与租户例外声明。</p>
 *
 * <p>本 Mapper 关闭 MyBatis-Plus 租户自动注入（{@code @InterceptorIgnore}），因为 JSqlParser 无法解析
 * pgvector 操作符与 ON CONFLICT；因此 XML 中每条语句都显式带 {@code tenant_id}，
 * 或只按全局唯一的 {@code chunk_key} 操作（规范 4.4）。</p>
 */
@Mapper
@com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine = "true")
public interface KbChunkMapper {

    /**
     * 条件 upsert：内容哈希未变化的行不重写文本、向量与更新时间。
     *
     * @param rows 切片行
     * @return 实际写入行数
     */
    int upsertChanged(@Param("rows") List<KbChunkUpsertRow> rows);

    /**
     * 把未变化切片重绑到新版本文档（版本切换专用，规范 9.5）。
     *
     * @param tenantId 租户（本 Mapper 关闭了租户自动注入，必须显式传）
     * @param kbDocId  新版本文档标识
     * @param keys     业务键
     * @return 重绑行数
     */
    int rebindDocId(@Param("tenantId") long tenantId,
                    @Param("kbDocId") long kbDocId,
                    @Param("keys") List<String> keys);

    /**
     * 查询同一逻辑文档（跨版本）已有的业务键与哈希。
     *
     * <p>按 chunk_key 前缀而非 kb_doc_id 查询：chunk_key 跨版本稳定且全局唯一（规范 4.3），
     * 版本切换时新版本文档标识是新的，若按 kb_doc_id 查会把所有切片都判成“新增”，
     * 进而绕过增量、触发整篇重嵌入（规范 9.5 明确禁止）。</p>
     *
     * @param tenantId 租户（本 Mapper 关闭了租户自动注入，必须显式传）
     * @param prefix   chunk_key 的 LIKE 模式，形如 {@code docKey#%}
     * @return chunk_key 到 content_hash 的映射
     */
    List<Map<String, Object>> selectHashesByDocKey(@Param("tenantId") long tenantId,
                                                   @Param("prefix") String prefix);

    /**
     * 查询某文档已有的业务键与哈希。
     *
     * @param kbDocId 文档标识
     * @return chunk_key 到 content_hash 的映射
     */
    List<Map<String, Object>> selectHashes(@Param("kbDocId") long kbDocId);

    /**
     * 查询某文档全部切片，供同步后核对。
     *
     * @param kbDocId 文档标识
     * @return 切片行
     */
    List<KbChunkSearchRow> selectByDoc(@Param("kbDocId") long kbDocId);

    /**
     * 按业务键物理删除过期切片。
     *
     * @param keys 业务键
     * @return 删除行数
     */
    int deleteByKeys(@Param("keys") List<String> keys);

    /**
     * 统计文档切片数。
     *
     * @param kbDocId 文档标识
     * @return 切片数
     */
    int countByDoc(@Param("kbDocId") long kbDocId);

    /**
     * 向量路召回（规范 6.2）：只查上架版本、限定租户，按余弦距离升序。
     *
     * @param queryVector 查询向量字面量
     * @param clauseType  条款类型，可为空
     * @param tenantId    租户
     * @param limit       条数上限
     * @return 召回行
     */
    List<KbChunkSearchRow> vectorSearch(@Param("queryVector") String queryVector,
                                        @Param("clauseType") String clauseType,
                                        @Param("tenantId") long tenantId,
                                        @Param("limit") int limit);

    /**
     * 关键词路召回（规范 6.2）：全文检索限定租户与上架版本，按 ts_rank_cd 降序。
     *
     * @param query      查询文本
     * @param clauseType 条款类型，可为空
     * @param tenantId   租户
     * @param limit      条数上限
     * @return 召回行
     */
    List<KbChunkSearchRow> keywordSearch(@Param("query") String query,
                                         @Param("clauseType") String clauseType,
                                         @Param("tenantId") long tenantId,
                                         @Param("limit") int limit);
}
