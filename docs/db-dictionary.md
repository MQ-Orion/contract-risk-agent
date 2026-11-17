# 数据字典

> 对应开发规范 4.1–4.3、5.5。表或枚举变更必须在同一 PR 内更新本文件（规范 14.5）。
> 数据库：PostgreSQL 16，时区统一 UTC；主键为长整型雪花标识；业务数据只做逻辑删除。

## 1. 通用约定

| 约定 | 说明 |
| --- | --- |
| 主键 | `id bigint`，雪花标识由应用生成（`SnowflakeIdGenerator`），非自增 |
| 审计字段 | `created_at`、`updated_at`（`timestamptz`，UTC）、`created_by`、`deleted`（0/1） |
| 时间 | 一律 `timestamptz` 存 UTC，展示层转东八区；禁止字符串存时间 |
| 金额 | `numeric(18,2)`（本轮表未涉及金额列） |
| 枚举 | 存 code（大写英文），中文只用于展示 |
| 删除 | 逻辑删除；唯一索引均带 `WHERE deleted = 0`，允许删除后重建同业务键 |
| 租户 | 所有业务表带 `tenant_id`，并建“租户 + 业务键”复合索引 |

## 2. 本轮已建表（第 1–2 周）

### 2.1 contract_main（合同主表，属主 doc-module）

| 列 | 类型 | 空 | 说明 |
| --- | --- | --- | --- |
| id | bigint | N | 主键，雪花标识 |
| tenant_id | bigint | N | 租户 |
| contract_no | varchar(64) | Y | 合同编号，第 8–9 周版本对比使用 |
| name | varchar(200) | N | 合同名称（取上传文件名，剔除路径部分） |
| file_hash | char(64) | N | 源文件 SHA-256，同租户内唯一（秒传依据） |
| file_path | varchar(512) | N | 存储相对路径：`{tenantId}/{yyyyMM}/{contractId}.{ext}` |
| file_format | varchar(16) | N | `PDF` / `DOC` / `DOCX`，由魔数判定 |
| file_size | bigint | N | 文件字节数 |
| page_count | integer | N | 页数，解析后回填；0 表示未知 |
| uploader_id | bigint | N | 上传人 |
| audit_status | varchar(32) | Y | 审核状态，解析阶段为 NULL（第 8–9 周由 audit 模块维护） |
| version_no | integer | N | 版本号，默认 1 |
| created_at / updated_at | timestamptz | N | 审计时间 |
| created_by | bigint | Y | 创建人 |
| deleted | smallint | N | 逻辑删除标记，0 有效 |

索引：`uk_contract_main_tenant_hash (tenant_id, file_hash) WHERE deleted = 0`。

### 2.2 contract_parse_task（解析任务，属主 doc-module）

| 列 | 类型 | 空 | 说明 |
| --- | --- | --- | --- |
| id | bigint | N | 主键 |
| tenant_id | bigint | N | 租户 |
| contract_id | bigint | N | 合同标识，外键指向 `contract_main.id` |
| status | varchar(16) | N | 见 4.1 解析状态枚举 |
| retry_count | integer | N | 已重试次数，上限 3 |
| next_retry_at | timestamptz | Y | 下次可重试时间（指数退避 1/4/16 分钟） |
| error_code | varchar(32) | Y | 失败错误码名称，如 `PARSE_FAILED` |
| error_message | varchar(512) | Y | 失败原因（仅运维可见，不对外返回） |
| started_at / finished_at | timestamptz | Y | 开始与结束时间 |
| created_at / updated_at | timestamptz | N | 审计时间 |
| created_by | bigint | Y | 创建人 |
| deleted | smallint | N | 逻辑删除标记 |

索引：`idx_parse_task_tenant_contract (tenant_id, contract_id, created_at DESC)`。

### 2.3 contract_chunk（合同切片，属主 doc-module）

| 列 | 类型 | 空 | 说明 |
| --- | --- | --- | --- |
| id | bigint | N | 主键 |
| tenant_id | bigint | N | 租户 |
| contract_id | bigint | N | 合同标识，外键 |
| clause_no | varchar(32) | N | 条款序号，如 `第三条`、`1.1`、`（一）`、`前言` |
| chunk_type | varchar(32) | N | 条款类型 code，见 4.2 |
| type_source | varchar(16) | N | 标签来源，见 4.3 |
| chunk_text | text | N | 切片文本 |
| full_text_index | integer | N | 全文递增索引，从 0 开始 |
| char_start | integer | N | 原文起始偏移（含） |
| char_end | integer | N | 原文结束偏移（不含） |
| tsv | tsvector | Y | 全文列，**由触发器维护，应用层禁止写入** |
| created_at / updated_at | timestamptz | N | 审计时间 |
| created_by | bigint | Y | 创建人 |
| deleted | smallint | N | 逻辑删除标记 |

索引：`uk_contract_chunk_index (contract_id, full_text_index) WHERE deleted = 0`、
`idx_contract_chunk_tsv`（GIN）、`idx_contract_chunk_tenant_type (tenant_id, chunk_type)`。

触发器：`trg_contract_chunk_tsv`，在 `INSERT` 或 `UPDATE OF chunk_text` 时调用
`crag_set_tsv()`，以分词配置 `crag_cn` 生成 `tsv`（规范 4.3）。

**注意**：本表不存 `embedding`。合同条款只作为查询输入，不参与向量召回（规范 4.2）。

## 3. 知识库表（第 3–4 周新增）

### 3.1 kb_doc（知识库文档，属主 kb-module）

| 列 | 类型 | 空 | 说明 |
| --- | --- | --- | --- |
| id | bigint | N | 主键 |
| tenant_id | bigint | N | 租户 |
| doc_key | varchar(64) | N | 逻辑文档标识，由标题规范化后哈希得到，跨版本稳定 |
| title | varchar(200) | N | 标题 |
| doc_type | varchar(16) | N | `INSTITUTION` 制度 / `CASE` 判例 |
| version_no | integer | N | 版本号，从 1 开始 |
| is_active | smallint | N | 上下架：1 上架、0 下架；检索只查上架版本 |
| source_hash | char(64) | N | 源文本 SHA-256 |
| synced_file_hash | char(64) | Y | 上次同步完成时的源哈希快照，供每日对账只读判定（规范 5.6） |
| last_synced_at | timestamptz | Y | 上次同步完成时间 |
| sync_status | varchar(16) | N | `PENDING` / `EMBEDDING` / `READY` / `FAILED` |
| content_text | text | N | 已解析正文；对账与重建切片只读本列，不重新解析源文件 |
| created_at / updated_at | timestamptz | N | 审计时间 |
| created_by | bigint | Y | 创建人 |
| deleted | smallint | N | 逻辑删除标记 |

索引：`uk_kb_doc_key_version (tenant_id, doc_key, version_no) WHERE deleted = 0`、`idx_kb_doc_tenant_active`。

### 3.2 kb_chunk（知识库切片，属主 kb-module）

| 列 | 类型 | 空 | 说明 |
| --- | --- | --- | --- |
| id | bigint | N | 主键 |
| tenant_id | bigint | N | 租户 |
| kb_doc_id | bigint | N | 文档标识，外键 |
| chunk_key | varchar(160) | N | 由 `doc_key#条款序号#分片序号` 生成，跨版本稳定，条件 upsert 的冲突键 |
| clause_no | varchar(32) | N | 条款序号 |
| chunk_text | text | N | 切片文本 |
| chunk_type | varchar(32) | N | 六类条款类型 code |
| content_hash | char(64) | N | 规范化文本 SHA-256，变更判据 |
| embedding | vector(1024) | Y | 向量列，维度与模型一致 |
| tsv | tsvector | Y | 全文列，由 `crag_set_tsv()` 触发器维护 |
| created_at / updated_at | timestamptz | N | 审计时间（未变化切片不更新 `updated_at`） |
| created_by | bigint | Y | 创建人 |
| deleted | smallint | N | 逻辑删除标记（过期切片物理删除，见下） |

索引：`uk_kb_chunk_key`（全局唯一）、`idx_kb_chunk_embedding`（HNSW，m=16、ef_construction=64、cosine）、
`idx_kb_chunk_tsv`（GIN）、`idx_kb_chunk_tenant_type`、`idx_kb_chunk_doc`。

**为什么过期切片物理删除**：`uk_kb_chunk_key` 按规范 4.3 为全局唯一索引，逻辑删除会让旧行继续占用业务键，
导致同一 `chunk_key` 无法重建；切片是正文的派生数据而非业务事实，因此删除条款时物理删除。
该口径记录在 ADR 0004 与迁移脚本注释中。

## 4. 第 5–7 周新增表

### 4.1 rule_config（规则配置，属主 agent-module）

| 列 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 雪花主键 |
| tenant_id | bigint | 租户；灰度按租户生效（规范 8.3） |
| rule_code | varchar(64) | 三段式编码：条款类型.业务点.序号 |
| rule_name | varchar(128) | 规则名称 |
| clause_type | varchar(32) | 适用条款类型；合同级规则固定 `CONTRACT` |
| scope | varchar(16) | `CLAUSE` 单条款 / `CONTRACT` 合同级 |
| condition_expr | varchar(500) | Aviator 表达式，变量白名单见规范 8.2 |
| risk_level | varchar(16) | `HIGH` / `MEDIUM` / `LOW`（规范 9.2） |
| suggestion_template | varchar(500) | 建议模板 |
| enabled | smallint | 0 关闭（灰度试跑态）/ 1 启用 |
| version_no | integer | 版本号，每次变更 +1 |
| positive_example / negative_example | text（`IS JSON` 约束） | 回放用例，含完整求值上下文与 expected |
| owner_name | varchar(64) | 维护人 |
| created_at / updated_at / created_by / deleted | — | 审计四字段 |

索引：`uk_rule_config_tenant_code`（租户 + 编码，部分唯一，`deleted = 0`）、`idx_rule_config_tenant_type`、`idx_rule_config_updated`。

**为什么正反例用带 JSON 约束的 text 而非 jsonb**：回放需要完整上下文（条款文本、金额、账期、类型集合），
只有一段文本复现不出求值条件；列不做 JSON 运算符查询，`text + IS JSON` 已足够，同时避免 jsonb 写入需要 PGobject 类型处理器。

### 4.2 rule_change_log（规则变更日志，属主 agent-module）

只追加：`id`、`tenant_id`、`rule_id`、`rule_code`、`action`（CREATE/UPDATE/ENABLE/DISABLE/GRAY）、
`version_before`、`version_after`、`before_json`、`after_json`、`operator_id`、`operator_name`、`created_at`。
`before_json`/`after_json` 为 jsonb，由 Mapper 显式 `CAST(... AS jsonb)` 写入。

**为什么没有 updated_at 与 deleted**：只追加表不存在“更新/删除”，加这两个字段会让审计表看起来可改（规范 4.1 的删除标记对只追加表无意义）。

### 4.3 model_call_log（模型调用日志，属主 agent-module）

只追加：`id`、`tenant_id`、`contract_id`、`clause_no`、`purpose`（EXTRACT/RISK_JUDGEMENT/CLAUSE_TYPE）、
`model_name`、`model_version`、`prompt_version`、`prompt_tokens`、`completion_tokens`、`total_tokens`、
`latency_millis`、`degraded`、`degrade_reason`、`trace_id`、`created_at`。

规范 7.6 要求的五项（模型名与版本、Prompt 版本、令牌数、耗时、是否降级）全部落在此表，是成本核算与质量排查的唯一依据。

### 4.4 contract_extract_info（合同抽取结果，属主 doc-module）

| 列 | 类型 | 说明 |
| --- | --- | --- |
| id / tenant_id / contract_id | bigint | 标识 |
| contract_amount | numeric(18,2) | 合同金额；未回验通过时为 null |
| payment_days | integer | 账期天数 |
| jurisdiction | varchar(128) | 管辖地 |
| has_joint_liability | smallint | 是否含连带责任 |
| extract_status | varchar(24) | `VERIFIED` / `PENDING_MANUAL` / `DEGRADED` |
| model_raw_response | text（`IS JSON`） | 模型原始返回（截断），仅供回溯 |
| source_span_json | text（`IS JSON`，必填） | 每字段的值、片段、回验结果与失败原因 |
| prompt_version / model_name | varchar | 版本追溯 |

索引：`uk_extract_contract`（租户 + 合同，部分唯一，`deleted = 0`）。
抽取由 agent 调用模型完成，但表归属 doc，agent 通过 `ContractExtractionRepository` 端口写入（规范 2.2）。

## 5. 第 8–9 周新增表

### 5.1 audit_task（审核任务，属主 audit-module）

| 列 | 说明 |
| --- | --- |
| id / tenant_id / contract_id / contract_version | 标识；四者与版本一起唯一（部分唯一索引，排除 CANCELLED/FAILED） |
| status | `CREATED` / `PARSING` / `AUDITING` / `PENDING_REVIEW` / `COMPLETED` / `FAILED` / `CANCELLED` |
| auditor_id | 最近一次状态变更的操作人 |
| conclusion | 人工结论摘要（模型结论禁止直接写入） |
| model_version / prompt_version | 回溯与回归比对 |
| report_url | 最近一次导出文件位置（不是报告正文副本） |
| started_at / finished_at | 起止时间 |
| created_at / updated_at / created_by / deleted | 审计四字段 |

索引：`uk_audit_task_contract_version`（同合同同版本禁止并发任务，规范 9.1）、`idx_audit_task_tenant_status`。

### 5.2 audit_risk_item（风险明细，属主 audit-module）

等级、内容、建议、`source`（RULE/MODEL/RULE_CONFLICT）、规则编码与版本、来源文档名、来源切片键、
引用原文、引用回验标记、条款序号/类型、`clause_text` 与 `retrieval_snapshot`（回流上下文）、
`reviewed`、`review_result`、`reviewed_by/at`、`version_no`（乐观锁，规范 10.7）、
`superseded`（0 当前轮 / 1 历史轮；驳回重审时旧项置 1，不物理删除，ADR 0008）。

索引：`idx_risk_item_task`、`idx_risk_item_unreviewed`、`idx_risk_item_task_active`（部分索引，只覆盖当前轮）。

### 5.3 audit_review_log（复核日志，属主 audit-module）

只追加：任务、风险项、复核人、`action`（PASS/REJECT/NEED_FIX）、`reason_code`（四类固定，驳回必填）、
备注、链路标识、时间。表级 CHECK 保证“驳回必须带原因码”。

索引：`idx_review_log_task`、`idx_review_log_reason`。

### 5.4 user_permission（用户权限，属主 audit-module）

用户、租户、`role`（ADMIN/AUDITOR/REVIEWER/VIEWER）、`doc_scope`（ALL/TENANT/OWN/NONE）。
唯一索引 `uk_user_permission_tenant_user`（租户+用户，部分唯一）。角色决定动作、数据域决定可见范围（规范 11.1）。
## 6. 枚举

### 6.1 解析状态 ParseStatusEnum

| code | 中文 | 允许流转到 |
| --- | --- | --- |
| PENDING | 待处理 | PARSING、FAILED |
| PARSING | 解析中 | SPLITTING、FAILED |
| SPLITTING | 切分中 | EMBEDDING、READY、FAILED |
| EMBEDDING | 向量化中（仅知识库同步） | READY、FAILED |
| READY | 就绪 | — |
| FAILED | 失败 | PENDING（人工或自动重试） |

### 6.2 条款类型 ClauseTypeEnum

`BREACH`（违约责任）、`CONFIDENTIALITY`（保密）、`PAYMENT`（付款）、`JURISDICTION`（管辖）、
`DISCLAIMER`（免责）、`OTHER`（其他）。新增类型必须同时更新规则配置与检索映射（规范 5.3）。

### 6.3 标签来源 TypeSourceEnum

`KEYWORD`（标题/正文关键词命中）、`MODEL`（模型兜底）、`MANUAL`（人工修正）、`DEFAULT`（未命中）。

### 6.4 文档格式 DocumentFormatEnum

`PDF`、`DOC`、`DOCX`，由魔数判定且必须与扩展名一致。

### 6.5 规则作用域 RuleScopeEnum

`CLAUSE`（单条款求值）、`CONTRACT`（合同级求值，可读 `presentTypes` 判断必备条款缺失）。

### 6.6 风险等级 RiskLevelEnum

`HIGH`（高，必须人工复核）、`MEDIUM`（中，必须人工复核）、`LOW`（低，可批量复核）。口径见规范 9.2 表 11。

### 6.7 抽取状态 ExtractStatusEnum

`VERIFIED`（全部有值字段回验通过）、`PENDING_MANUAL`（存在无片段或回验失败字段）、`DEGRADED`（模型不可用或输出连续两次不合法）。

### 6.8 规则变更动作 RuleChangeActionEnum

`CREATE`、`UPDATE`、`ENABLE`、`DISABLE`、`GRAY`。

### 6.9 审核任务状态 AuditTaskStatusEnum

`CREATED`（已创建）、`PARSING`（解析中，当前链路保留未用）、`AUDITING`（审核中）、`PENDING_REVIEW`（待复核）、
`COMPLETED`（已完成）、`FAILED`（失败）、`CANCELLED`（已取消）。白名单边见 `AuditTaskStatusEnum`，
唯一回退为 `PENDING_REVIEW → AUDITING`（规范 9.1）。

### 6.10 复核动作 ReviewActionEnum

`PASS`（通过）、`REJECT`（驳回，必须带原因码）、`NEED_FIX`（待修改）。

### 6.11 驳回原因码 RejectReasonCodeEnum

`IRRELEVANT_RETRIEVAL`（检索无关）、`WRONG_CITATION`（引用错误）、`RULE_FALSE_POSITIVE`（规则误报）、
`SEMANTIC_MISJUDGE`（语义误判）。固定四类，禁止临时新增（规范 9.4）。

### 6.12 用户角色与数据域

角色 `ADMIN` / `AUDITOR` / `REVIEWER` / `VIEWER`；数据域 `ALL` / `TENANT` / `OWN` / `NONE`（规范 11.1）。

## 7. 初始化与迁移

- `db/init/`：由容器 entrypoint 以超级用户执行，负责扩展（pgvector、zhparser）、分词配置（`crag_cn`）与通用函数
  （`crag_set_tsv`）。应用侧迁移**不得**创建扩展。
  - `crag_cn` 的解析器为 **zhparser 2.4**（依赖 SCWS 1.2.3，随 `docker/postgres/Dockerfile` 编译进镜像）；
    映射按解析器全部词性生成（标点类型 `w` 除外），并写入 `违约金/连带责任/管辖地/质保期/账期/免责/保密/争议解决`
    八条自定义词（`zhparser.zhprs_custom_word`）。
  - 未安装中文分词扩展时脚本会退化为 simple 并告警，属阶段方案，须两周期限内整改（规范 6.5）。
- `db/migration/`：Flyway 命名 `V{yyyyMMddHHmm}__{desc}.sql`，已执行文件禁止修改，只能新增。
- 破坏性变更四步走：加列 → 双写与回填 → 切换读写 → 删除旧列，每步独立 PR（规范 4.5）。
