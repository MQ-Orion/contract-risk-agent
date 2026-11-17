package com.crag.kb.application.port;

import com.crag.kb.domain.model.KbDocRecord;
import java.util.List;
import java.util.Optional;

/**
 * 知识库文档仓储端口（表 kb_doc 属主为 kb-module，规范 2.2 单写者原则）。
 */
public interface KbDocRepository {

    /**
     * 按租户与标识查询。
     *
     * @param tenantId 租户
     * @param docId    文档标识
     * @return 文档
     */
    Optional<KbDocRecord> findById(long tenantId, long docId);

    /**
     * 按逻辑标识与版本查询。
     *
     * @param tenantId  租户
     * @param docKey    逻辑文档标识
     * @param versionNo 版本号
     * @return 文档
     */
    Optional<KbDocRecord> findByDocKeyAndVersion(long tenantId, String docKey, int versionNo);

    /**
     * 查询同一逻辑文档的全部版本，按版本号倒序。
     *
     * @param tenantId 租户
     * @param docKey   逻辑文档标识
     * @return 版本列表
     */
    List<KbDocRecord> listVersions(long tenantId, String docKey);

    /**
     * 查询租户下文档，可按上架状态过滤。
     *
     * @param tenantId 租户
     * @param active   上架状态；null 表示不过滤
     * @return 文档列表
     */
    List<KbDocRecord> list(long tenantId, Boolean active);

    /**
     * 新增文档。
     *
     * @param doc 文档
     * @return 已落库文档
     */
    KbDocRecord insert(KbDocRecord doc);

    /**
     * 更新文档（同步状态、上下架、同步快照）。
     *
     * @param doc 文档
     */
    void update(KbDocRecord doc);

    /**
     * 只更新上下架状态（不覆盖正文/同步状态/源哈希）。
     *
     * <p>与 {@link #update(KbDocRecord)} 分开的原因：异步同步落库时携带的是同步开始时的快照，
     * 如果连 is_active 一起写回，会把用户在同步期间做的上下架覆盖掉（多版本切换的并发缺陷）。</p>
     *
     * @param tenantId 租户
     * @param docId    文档标识
     * @param active   目标状态
     */
    void updateActive(long tenantId, long docId, boolean active);

    /**
     * 查询有在架文档的租户列表，供每日对账任务遍历。
     *
     * @return 租户标识
     */
    List<Long> findActiveTenants();

    /**
     * 查询需要同步的文档：上架且同步哈希与源哈希不一致。
     *
     * @param tenantId 租户
     * @return 待同步文档
     */
    List<KbDocRecord> findPendingSync(long tenantId);
}
