# 运维手册

> 对应开发规范 15.1–15.5、16.1。部署、回滚或备份方式变更必须更新本文件。

## 1. 本地环境

| 组件 | 版本 | 获取方式 |
| --- | --- | --- |
| JDK | 17 LTS | 本地安装，`JAVA_HOME` 指向 17 |
| Maven | 3.9.x | 本地安装 |
| PostgreSQL | 16 + pgvector + zhparser | 本地构建镜像 `crag-postgres:16-zhparser`（宿主端口 **5434**） |
| Redis | 8.x（已批准，见 ADR 0002 偏差 10） | `docker/compose.yaml`（镜像 `redis:8-alpine`，宿主端口 **6381**） |

依赖环境一条命令拉起（首次会构建带 zhparser 的数据库镜像，需要网络）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\dev-up.ps1          # 构建/启动 + 健康检查 + 自检
powershell -ExecutionPolicy Bypass -File scripts\dev-up.ps1 -SkipVerify
```

单独构建数据库镜像：

```powershell
docker compose -f docker/compose.yaml build postgres
```

镜像基于 `pgvector/pgvector:pg16`，在其中编译安装 SCWS 1.2.3 与 zhparser 2.4（源码见
`docker/postgres/Dockerfile`）。若使用未经构建的官方 pgvector 镜像，`crag_cn` 会退化为 simple 并告警。

**环境必须可从零重建**：删除 `docker/pgdata` 卷后重新 `docker compose up -d`，`db/init` 会重新执行。
禁止依赖本机已安装的数据库实例（规范 15.1）。

## 2. 数据库初始化顺序

1. 容器 entrypoint 以超级用户执行 `db/init/*.sql`（扩展、`crag_cn` 分词配置、`crag_set_tsv()`）；
2. 应用启动时由 Flyway 执行 `db/migration/*.sql`（第 3–4 周接入 Flyway）。

`db/init/00_extensions.sql` 在 pgvector 缺失时会**立即失败**，这是刻意设计：不带病启动。

当前环境已安装 zhparser 2.4，`crag_cn` 的解析器为 zhparser，中文关键词可正常召回。

## 3. 健康检查与启动顺序

- 启动顺序：数据库/缓存就绪 → Flyway 迁移 → 应用；**迁移失败必须中止启动**（Flyway 默认行为）。
- 健康检查必须覆盖 DB 与 Redis 连通性：`curl.exe http://127.0.0.1:8080/actuator/health`（若未开
  actuator，则用 `GET /api/v1/rules/cache` 同时验证数据库与缓存，返回 `code=0` 即通过）。
- 配置只经环境变量注入：`DASHSCOPE_API_KEY`（必需）、`DB_HOST/DB_PORT/DB_USER/DB_PASSWORD`、
  `REDIS_HOST/REDIS_PORT`、`CHAT_MODEL`、`EMBEDDING_MODEL` 等，默认值见 `application.yml`。

## 4. 发布与回滚（规范 15.4，四项缺一不可）

1. **镜像回滚**：保留上一版本镜像标签（标签内记录提交短哈希）；回滚时切回上一标签并重启。
2. **迁移回滚判定**：Flyway 迁移只增不改；破坏性变更按“加列 → 双写回填 → 切读写 → 删旧列”四步，
   每步独立发布。不可逆迁移（如删除列）发布前必须备份，并在发布单标注不可回滚。
3. **缓存清理**：回滚后 Redis 中的检索缓存键含索引版本，版本不变则可能命中旧结果；回滚后执行
   `POST /api/v1/rules/refresh` 并在运维侧清理 `retr:*` 键（或等待 TTL 30 分钟）。
4. **回滚后评估验证**：重跑 `scripts/eval-all.ps1`，命中率不得低于基线 2 个百分点（规范 6.7、15.5）。

## 5. 备份与恢复（规范 15.5）

- 每日全量 + 增量归档，保留 30 天：`pg_dump -Fc` 每日全量，WAL 归档做增量。
- **向量可重算构成灾备底线**：`kb_doc.content_text` 与 `chunk_key` 是重建依据；恢复流程为
  恢复数据库 → 对每个 `kb_doc` 调 `POST /api/v1/kb/documents/{id}/sync` 触发条件 upsert 重建向量。
- 恢复时间目标 ≤4 小时；每季度一次恢复演练并归档结果。
- 恢复完成后**必须重跑评估集**，确认检索指标未退化（数据回来了但指标退化等于没恢复）。

## 6. 定期任务

| 任务 | 频率 | 说明 |
| --- | --- | --- |
| 知识库每日对账 | 每日 02:30 | `KbReconciliationJob`，ShedLock 互斥，只读哈希判定，不重新解析 |
| 规则缓存刷新 | 30 秒 | `RuleCacheRefreshJob`，ShedLock + 幂等替换 |
| hard negatives 导出 | 每日（建议） | 当前手动 `POST /api/v1/audit/hard-negatives/export`；接入调度后按租户导出 |
| 评估跑批 | 每次检索/Prompt/规则/切分变更 | `scripts/eval-all.ps1`，结果归档 `docs/eval/` |
| 备份与恢复演练 | 每日 / 每季度 | 见第 5 节 |

## 7. 故障排查入口

| 现象 | 先看哪里 |
| --- | --- |
| 启动失败且提示缺少密钥 | 环境变量 `DASHSCOPE_API_KEY`（规范 11.5，缺失即失败） |
| 迁移失败 | `flyway_schema_history` 与 `db/migration`；已执行脚本不得修改 |
| 检索为空 | 知识库文档 `sync_status`、`is_active`、索引版本；`POST /api/v1/rules/refresh` 无关，检索缓存在 `retr:*` |
| 审核任务停在待复核 | 正常，需要人工复核；未复核高/中风险会阻止完成（40001） |
| SSE 无事件 | 任务是否已创建；连接是否按任务标识；服务端日志 `SSE 推送失败` |
| 覆盖率报告缺失 | JaCoCo destfile 是否在 ASCII 路径（见 `docs/pitfalls.md` 2.4） |

## 8. 变更记录

- 2026-11-16：补充审核/复核/SSE/权限相关排障；新增发布回滚四项、备份恢复与定期任务；修正健康检查说明。
