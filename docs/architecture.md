# 架构说明

> 对应开发规范 2.1、2.2、3.1–3.3。任何架构变更必须同步本文件（规范 14.5）。

## 1. 形态与判断依据

项目采用**模块化单体**：单一 Spring Boot 应用、单进程部署，六个 Maven 模块表达职责边界。
PostgreSQL 16 单库同时承载结构化主数据、向量索引（pgvector）与全文索引（tsvector）；Redis 承载会话、
任务状态、分布式锁与热点切片缓存。

选择依据是量级与团队规模：合同风控场景切片总量在十万至百万级以内，HNSW 索引可做到数十毫秒级检索；
团队小于五人时，独立向量库与全文检索引擎带来的运维成本与一致性成本高于收益。可拆分点留在两处真正有边界的
地方——检索接口收口与解析异步化——而不是提前拆成一堆进程（规范 2.1）。

## 2. 模块与依赖

模块之间只允许单向依赖，禁止环形依赖（规范 2.2）。

```
agent-module ──► doc-module
      │               ▲
      ├──► kb-module ─┘
      │
      ├──► retrieval-module ──► kb-module
      │
      └──► audit-module

common-module  ◄── 所有业务模块（禁止反向依赖业务模块）
```

| 调用方 | 允许被调用 | 约束 |
| --- | --- | --- |
| doc / kb / audit | common | 仅对外接口 |
| retrieval | common、kb | 仅 `KbChunkReadPort`；召回 SQL 在端口内部执行 |
| agent | doc、kb、retrieval、audit | 仅对外 Service 接口 |
| audit | agent | **禁止**，会形成循环依赖 |
| 任意业务模块 | 其他模块的 Mapper 或表 | **禁止**，破坏数据所有权 |

`agent-module` 位于依赖图顶端，因此唯一可启动的 Spring Boot 应用类
（`com.crag.agent.ContractRiskAgentApplication`）放在该模块；这与“单进程部署”一致，且不引入额外模块。

## 3. 数据所有权（单写者）

| 表 | 属主模块 | 本轮状态 |
| --- | --- | --- |
| `contract_main` | doc | 已建 |
| `contract_parse_task` | doc | 已建 |
| `contract_chunk` | doc | 已建 |
| `kb_doc` / `kb_chunk` | kb | 第 3–4 周 |
| `audit_task` / `audit_risk_item` / `audit_review_log` | audit | 第 8–9 周 |
| `rule_config` | agent | 第 5–7 周 |
| `user_permission` | audit | 第 8–9 周 |

跨模块读取只能经由属主模块导出的只读接口；禁止注入对方 Mapper，禁止在自身 SQL 中 JOIN 对方表。
本轮 `doc-module` 的仓储端口（`ContractRepository`、`ParseTaskRepository`、`ContractChunkRepository`）
即为将来对外的只读接口雏形。

## 4. 分层与包结构

每个模块内部固定五层：`api`、`application`、`domain`、`infrastructure`、`config`，
包名 `com.crag.{module}.{layer}`（规范 3.2）。

| 层 | 职责 | 禁止 |
| --- | --- | --- |
| api | 入参校验、DTO 转换、调用 application | 写业务分支、直接访问 Mapper |
| application | 用例编排、事务边界、幂等控制 | 拼接 SQL、内联 Prompt |
| domain | 切分算法、类型判定、状态机、格式校验 | 依赖 HTTP 或数据库实现 |
| infrastructure | Tika/POI/PDFBox 解析、存储、锁、持久化 | 承载业务规则 |
| config | Bean 装配与配置绑定 | 写业务逻辑 |

事务边界固定在 application 层：`ParseProgressWriter` 只包裹落库动作，解析与切分在事务外执行（规范 3.8）。

## 5. 解析链路

```
上传 → 白名单+魔数校验 → 临时文件+SHA-256 → 同租户同哈希秒传？
     → 落盘（租户/年月/标识.扩展名） → contract_main + contract_parse_task(PENDING)
     → 解析线程池提交 → 分布式锁 lock:parse:{hash}（10 分钟）
     → Tika 取文本 → 文本质量校验（空/乱码>5% 判失败）
     → 页数校验（≤300） → 条款切分（正则粗分 + 800 字窗口 + 10% 重叠 + 六类标签）
     → 单事务：切片落库 + 回填页数 + 状态置 READY
```

失败处理：记录错误码与重试次数，按 1/4/16 分钟计算 `next_retry_at`，最多 3 次，管理端可人工重试（规范 5.5）。

### 5.1 关键设计取舍

- **表格不接受 Tika 输出**：DOCX 用 POI 按 `gridSpan`/`vMerge` 还原网格，PDF 用字符坐标按列锚点重建行列；
  错位表格会污染切分，因此 PDF 侧宁可漏识别也不产出错位行列（规范 5.2）。
- **doc-module 不调用模型**：条款类型先走关键词；未命中落到“其他 + DEFAULT”，由
  `ClauseTypeFallbackPort` 兜底。该端口默认实现不调用模型，接入 LLM 的实现由 agent-module 提供，
  同时满足规范 2.1（doc 禁止调 LLM）与 5.3（模型兜底分类）。
- **中文分词**：`crag_cn` 由镜像内的 zhparser 2.4 + SCWS 1.2.3 提供（`docker/postgres/Dockerfile` 从源码编译），
  初始化脚本按解析器的全部词性建立映射，并写入 8 条合同高频自定义词；`tsv` 仍由触发器统一维护，
  应用层不感知分词实现（规范 4.3、6.5）。
- **合同切片不向量化**：按规范 4.2，合同条款只作为查询输入，不参与向量召回，因此解析状态机不经过
  `EMBEDDING` 态；该态保留给知识库文档增量同步。
- **页数只信可用来源**：PDF 用 PDFBox 取真实页数；DOCX 的页数只在渲染后写入文档属性，不同编辑器默认值不同，
  因此按“未知”处理（`page_count = 0`），不让错误的页数通过 300 页上限（ADR 0002 偏差 11）。
- **条款编号识别宁严勿宽**：阿拉伯数字必须带分级小数点（1.1）或分隔符（1、）才算条款标记，
  避免把“12 台 / 4 套”这类表格数量行切成伪条款（ADR 0002 偏差 12）。

## 6. 检索收口（尚未实现，接口先定）

`retrieval-module` 对外只暴露一个方法：

```java
RetrievalResult retrieve(String query, String clauseType, String tenantId);
```

返回结构包含 `chunks`、`degraded`、`degradeReason`、`fallback`、`snapshotVersion` 五个部分。
双路召回 SQL 收在 `kb-module` 的 `KbChunkReadPort` 内，检索模块只做融合与降级（规范 6.1、6.2）。

## 7. 身份与租户

租户与操作人只能由服务端解析后注入上下文（`TenantContext`、`UserContext`），Controller 禁止自行解析，
禁止从请求体或查询参数读取（规范 10.3）。

当前实现为**开发期静态来源**：从服务端配置读取固定租户与操作人，接受任何客户端输入为零。
生产 profile 下 `crag.security.tenant-source=token`，因令牌解析器尚未实现，启动即失败而不是静默降级；
该能力的落地随第 8–9 周权限里程碑，见 `docs/adr/0002`。

## 8. 与规范基线的偏差

见 `docs/adr/0002-第1-2周技术选型与偏差登记.md`，每项均给出影响面、补偿措施与关闭时限。

## 9. 知识库与检索（第 3–4 周）

### 9.1 共享切分内核

切分算法下沉到 `common-module` 的 `com.crag.common.text`，doc 与 kb 使用同一个 Bean 与同一份 `crag.split.*` 参数，
满足规范 5.4「同一切分实现」与 2.2 依赖白名单的联立要求（决策见 ADR 0003）。
`FixedWindowSplitter` 只服务切分对比实验，留在 doc-module，不参与生产入库。

### 9.2 增量向量写入

`KbIndexSyncService` 执行规范 5.6 的五步流程：

1. 文档变更（新建上架 / 更新正文 / 上架）发布 `KbIndexChangedEvent`；
2. 事务提交后异步监听，按 `lock:embedding:{docId}` 加锁；
3. 用共享切分器切分 `content_text`，逐切片计算 `content_hash` 并与库中哈希比对；
4. 只有变化的切片调用嵌入接口，其余跳过（`SyncOutcome.skipped` 计入跳过率）；
5. 条件 upsert 写回，删除过期切片，最后发布索引变更事件让检索缓存失效。

嵌入调用在事务外，落库在 `KbSyncWriter` 的短事务中（规范 3.8）。模型为百炼
`qwen3.7-text-embedding-flash`（1024 维），密钥只走环境变量。

### 9.3 双路召回与融合

- 召回 SQL 收在 kb 的 `KbChunkReadPort` 实现内，检索模块只做融合与降级（规范 6.2）；
- 两路并行，单路 300ms 超时即降级并记录原因；
- RRF k=60，按切片标识去重后分数相加，再按最大值归一化，取前 6；
- 同类型为空时回退 `OTHER` 一次并标记 `fallback`；
- 缓存键为「租户 + 条款类型 + 查询哈希 + 索引版本」，TTL 30 分钟，索引变更按租户递增版本实现批量失效。

降级阈值作用于向量路首位余弦相似度（RRF 归一化后首位恒为 1，不能作为阈值判据），见 ADR 0004。

### 9.4 租户隔离

`common-module` 提供 MyBatis `TenantLineInnerInterceptor`，租户值来自服务端上下文；异步线程在入口设置上下文。
`KbChunkMapper` 因 pgvector 操作符无法被 JSqlParser 解析，改用 SQL 常量租户条件并关闭该 Mapper 的自动注入
（ADR 0004 第三节）。`TenantIsolationIT` 覆盖跨租户读取、跨租户检索与上下文缺失三类场景。
### 9.5 每日对账与定时任务互斥

`KbReconciliationJob` 每日凌晨执行一次轻量对账：只读 `kb_doc` 的 `source_hash` 与 `synced_file_hash`，
不一致（或从未同步）的在架文档触发增量同步，不重新解析源文件；单文档失败不阻断其它文档。
多实例互斥由 ShedLock（`@SchedulerLock(name=kbDailyReconcile)`）+ 现有 Redis 提供，不新增部署单元。
全量重解析属低频重量对账，手动触发，不纳入每日任务（规范 5.6）。
## 10. 初审链路（第 5–7 周）

```
ReviewController
   └─► ContractReviewOrchestrator.review            （固定顺序，规范 7.2）
         1. ContractExtractionService.extract       （模型 → 结构校验 → 原文回验 → doc 端口落库）
         2. ToolRegistry.ruleCheck().checkClause     （rule_check_tool，2s，确定性）
         3. ToolRegistry.retrieval().retrieve        （risk_retrieval_tool，5s，只经 RetrievalService）
         4. ClauseRiskService.judge                  （模型判断 + 引用校验）
         5. 合并冲突（规则优先，报告呈现双方结论）
         6. ToolRegistry.reportExport().export       （report_export_tool，10s，实时渲染 DOCX）
```

- **工具由注册中心统一注册**：编排器只持有 `ToolRegistry`，不构造具体工具；工具内部不调用其他工具（规范 7.1）。
- **规则引擎**：`rule_config` 表 + Aviator 5.4.3 + 30 秒缓存刷新（ShedLock + 幂等替换）。表达式经
  `ExpressionGuard` 标识符白名单与引擎特性关闭双层防护，求值超时 1 秒（规范 8.2）。
- **模型调用**：JDK HttpClient + 8 并发有界队列 + 熔断（1 分钟 10 次失败 → 5 分钟）+ 重试两次（1s/3s 退避）；
  连接 5s、读取 60s；每次调用写 `model_call_log`（规范 7.6）。
- **结构化抽取**：只送相关条款类型（≤6 段、每段 ≤800 字），输出按字段类型与数值范围校验，失败重试一次；
  片段缺失或逐字回验失败 → 该字段不落库、整体 `PENDING_MANUAL`（规范 7.4、7.5）。
- **报告**：由风险明细实时渲染，不保存正文副本；含生成时间、模型版本、Prompt 版本、规则版本、
  检索/模型降级标记、抽取回验通过率，页脚固定四条禁止与“不构成法律意见”声明（规范 9.3）。

降级路径：模型不可用或连续两次输出不合法 → `DEGRADED`，只出规则结论并标注；检索降级 → 风险项照常输出但
报告标注“部分依据可能缺失”；`rulesOnly=true` 等价于熔断期纯规则模式（规范 7.6）。

## 11. 审核、复核与流式进度（第 8–9 周）

```
AuditRunController (agent)
   └─► AuditRunService.start        （幂等建任务 → 置审核中 → 提交 auditRunExecutor）
          └─► ContractReviewOrchestrator.review
                 ├─ ContractExtractionService.extract
                 ├─ RuleCheckTool / RiskRetrievalTool / ClauseRiskService
                 └─ ReportExportTool
          └─► AuditTaskService.submitResult（风险项落库，任务进入待复核）

AuditStreamController  GET /audit/tasks/{id}/stream  → progress/risk/complete/error + 15s 心跳
AuditController        /audit/tasks/**              → 复核/完成/取消/hard negatives
```

- 状态机只允许白名单边，`PENDING_REVIEW → AUDITING` 是唯一回退；任务完成前校验不存在未复核的高/中风险。
- 复核使用 `version_no` 乐观锁，日志只追加；驳回必须带四类原因码之一。
- SSE 线程显式设置租户上下文；连接只读任务，重连不创建新任务（规范 10.6）。

## 12. 权限与评估（第 8–11 周）

- 权限：`PermissionPort`（common）由 `PermissionService`（audit，读 `user_permission`）实现；
  角色不足 10003，资源越权 10004；规则管理、审核发起、复核、合同读取分别接入。
- 评估：`scripts/eval-all.ps1` 依次跑检索基础、上下文召回、引用准确率、抽取回验，最后调用
  `scripts/eval-index.ps1` 生成带版本号的汇总；任何检索/Prompt/规则变更必须重跑（规范 6.7）。
- hard negatives：复核驳回 → 只增日志 → JSONL 导出 → 人工筛选进 `eval/retrieval/hard-negatives.json`。