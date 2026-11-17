# 企业合同风控智能审核 Agent

合同初审辅助平台：上传合同后自动完成**条款切分 → 规则校验 → 风控依据检索 → 模型判断 → 风险清单 → 人工复核 → DOCX 报告导出**，所有风险项都带来源切片与引用回验标记。

系统只做初审辅助，不做法律意见，也不替代法务决策。

## 功能特性

| 模块 | 能力 |
| --- | --- |
| 合同管理 | PDF / DOC / DOCX 上传，魔数校验、哈希秒传、Tika 解析、表格二次还原、条款级切分；租户合同列表、审核状态、任意状态逻辑删除 |
| 知识库 | 文档新建（文本 / PDF / DOC / DOCX 导入）、版本管理、上下架、增量向量写入、每日对账；同一标题只保留一个上架版本，可自由切换版本 |
| 规则引擎 | 规则配置表 + Aviator 表达式、正反例回放、试算、启停、热更新；规则变更只增版本，启停不改版本 |
| 检索 | pgvector 向量召回 + zhparser 全文召回，RRF 融合、超时降级、类型回退、按租户分代的检索缓存 |
| 初审编排 | 规则优先，语义类条款检索依据后由模型判断；工具注册中心统一注册，单条款调用上限、单合同总上限、整体超时 |
| 抽取回验 | 结构化抽取合同金额、账期、管辖地、连带责任；每个字段做原文片段回验，未通过转人工 |
| 审核任务 | 状态机、SSE 进度、风险明细、三类复核动作、四类驳回原因码、乐观锁、只增复核日志、驳回重审 |
| 报告 | DOCX 报告，包含风险表、来源切片、引用回验、模型/Prompt 版本、四条业务边界声明 |
| 权限 | 角色（ADMIN / AUDITOR / REVIEWER / VIEWER）+ 数据域（ALL / TENANT / OWN / NONE），默认拒绝、越权同码 |
| 前端 | Vue 3 管理系统布局：合同管理、知识库、审核任务、规则管理四个工作区 |

## 技术栈

| 分类 | 选型 |
| --- | --- |
| 语言 / 运行时 | JDK 17 |
| 后端框架 | Spring Boot 3.2.5（模块化单体） |
| 数据库 | PostgreSQL 16 + pgvector 0.8.7 + zhparser 2.4（中文分词配置 `crag_cn`） |
| 缓存 | Redis 8.x |
| 持久层 | MyBatis-Plus 3.5.5、Flyway 10.10.0 |
| 文档解析 | Apache Tika 2.9.1、POI 5.2.3、PDFBox |
| 规则表达式 | Aviator 5.4.3（沙箱：禁用特性、类白名单、1 秒超时） |
| 调度 | Spring Scheduling + ShedLock 5.13.0 |
| 模型 | 百炼 OpenAI 兼容接口：对话 `qwen3.8-flash`，向量 `qwen3.7-text-embedding-flash`（1024 维） |
| 前端 | Vue 3.4 + Vite 5 |

## 架构说明

单一 Spring Boot 应用、单进程部署，用 Maven 模块与包边界体现职责分离；模块之间只经端口/用例调用，不跨模块访问 Mapper。

| 模块 | 职责 |
| --- | --- |
| `common-module` | 统一响应与错误码、租户/操作人上下文、链路标识、雪花 ID、条款切分共享内核、租户拦截器、权限端口 |
| `doc-module` | 合同上传与存储、Tika 解析、表格还原、条款切分、解析状态机、抽取结果持久化 |
| `kb-module` | 知识库文档/版本/上下架、增量向量写入、双路召回 SQL、每日对账 |
| `retrieval-module` | 检索唯一出口、双路并行、RRF 融合、精度降级、缓存 |
| `audit-module` | 审核任务状态机、风险明细、复核日志、hard negatives 导出、权限实现 |
| `agent-module` | 规则引擎、工具注册中心、初审编排、模型客户端、抽取回验、DOCX 报告、审核发起、SSE；应用启动类 |
| `contract-web` | Vue 3 前端 |

依赖方向：`agent → doc / kb / retrieval / audit`，`retrieval → kb`，`kb / doc / audit → common`；禁止环形依赖与跨模块注入 Mapper。

## 快速开始

### 前置条件

- JDK 17、Maven 3.9+
- Docker Desktop（PostgreSQL / Redis 容器）
- Node.js 20+ 与 pnpm（仅前端）
- 一个可用的百炼 API Key：`DASHSCOPE_API_KEY`

### 1. 启动依赖环境

```powershell
docker compose -f docker/compose.yaml up -d
# 或使用脚本：构建带 zhparser 的镜像、启动、健康检查与自检
powershell -ExecutionPolicy Bypass -File scripts\dev-up.ps1
```

默认端口：PostgreSQL `5434`、Redis `6381`（刻意避开本机常见端口）。首次启动会自动执行 `db/init` 安装扩展并创建中文分词配置。

### 2. 构建与测试

```powershell
mvn -o -B clean verify
```

### 3. 启动后端

```powershell
$env:DASHSCOPE_API_KEY="你的百炼 Key"
$env:DB_USER="crag"
$env:DB_PASSWORD="crag-dev"
mvn -o -B -pl agent-module -am package -DskipTests
java -jar agent-module\target\agent-module-0.1.0.jar
```

服务地址 `http://127.0.0.1:8080`，接口前缀 `/api/v1`。

### 4. 启动前端

```powershell
cd contract-web
pnpm install
pnpm dev
```

打开 `http://127.0.0.1:5173`，`/api` 由 Vite 代理到 8080。

### 5. 端到端冒烟

```powershell
powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
```

脚本会上传 `eval/samples` 下三份示例合同、轮询解析进度、核对切片数量与秒传行为。

## 配置说明

配置全部支持环境变量覆盖；密钥只通过环境变量注入，不写入仓库。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DASHSCOPE_API_KEY` | 无 | **必填**，对话与向量模型共用 |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `127.0.0.1` / `5434` / `contract_risk` | PostgreSQL |
| `DB_USER` / `DB_PASSWORD` | `crag` / **无（必填）** | 密码无仓库默认值，必须显式注入；本地与 `docker/.env.example` 保持一致 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `127.0.0.1` / `6381` / 空 | Redis |
| `CHAT_MODEL` / `EMBEDDING_MODEL` | `qwen3.8-flash` / `qwen3.7-text-embedding-flash` | 模型名称 |
| `CRAG_STORAGE_ROOT` | `./data/contracts` | 合同原文存储目录 |
| `CRAG_REPORT_ROOT` | `./data/reports` | 报告输出目录 |
| `CRAG_LOCK_MODE` | `redis` | 分布式锁实现 |
| `CRAG_WORKER_ID` | `0` | 雪花 ID 机器号，多实例部署时必须唯一 |

## 使用流程

1. **上传合同**：`合同管理 → 上传并解析`，等待解析状态变为“就绪”；
2. **发起初审**：点“发起初审”或合同列表行内“审核”，后端异步执行，前端通过 SSE 展示进度；
3. **查看报告**：风险项分“规则 / 模型判定 / 规则与模型冲突”三类，冲突以规则为准并提示人工确认；
4. **人工复核**：逐条选择通过 / 驳回 / 待修改；驳回必须选择原因码（检索无关、引用错误、规则误报、语义误判）；
5. **完成审核**：所有高/中风险复核完成后可完成，系统生成 DOCX 报告，可下载；
6. **知识库维护**：制度/判例支持文本录入或 PDF/DOC/DOCX 导入，上架即触发增量向量写入；更新正文只重嵌入变化切片。

## 接口概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/contracts` | 上传合同（multipart 字段 `file`） |
| GET | `/api/v1/contracts` | 当前租户合同列表（含审核状态与任务 ID） |
| GET | `/api/v1/contracts/{id}` / `/{id}/clauses` / `/{id}/parse-task` | 详情、切片、解析进度 |
| DELETE | `/api/v1/contracts/{id}` | 逻辑删除合同与切片，审核历史保留 |
| POST | `/api/v1/contracts/{id}/review` | 初审预演（不落审核任务） |
| POST/GET/PUT | `/api/v1/kb/documents`、`/{id}`、`/{id}/content`、`/{id}/publish`、`/{id}/unpublish`、`/{id}/sync`、`/upload` | 知识库管理与文件导入 |
| GET/POST/PUT | `/api/v1/rules`、`/{id}`、`/{id}/enable`、`/{id}/disable`、`/{id}/dry-run`、`/refresh`、`/cache` | 规则管理与热更新 |
| POST | `/api/v1/audit/tasks/run` | 发起审核（幂等） |
| GET | `/api/v1/audit/tasks/latest` | 按合同查最近一次任务 |
| GET | `/api/v1/audit/tasks/{id}`、`/{id}/risks`、`/{id}/stream` | 任务、风险清单、SSE 进度 |
| POST | `/api/v1/audit/tasks/{id}/risks/{riskId}/review` | 复核（乐观锁 + 原因码） |
| POST | `/api/v1/audit/tasks/{id}/complete` / `/{id}/cancel` | 完成 / 取消 |
| GET | `/api/v1/audit/tasks/{id}/report` | 下载 DOCX 报告 |
| POST | `/api/v1/audit/hard-negatives/export` | 导出驳回样本（JSONL） |

完整契约见 [`docs/openapi.yaml`](docs/openapi.yaml)。

## 评估

评估集全部为虚构数据，覆盖付款、违约、保密、管辖、免责五类，并单列 hard negatives。

```powershell
# 需要 PostgreSQL/Redis 已启动，且已注入 DASHSCOPE_API_KEY
powershell -ExecutionPolicy Bypass -File scripts\eval-all.ps1

# 只跑检索与引用，不调用对话模型
powershell -ExecutionPolicy Bypass -File scripts\eval-all.ps1 -SkipExtraction
```

最近一次实测（2026-11-16）：

| 指标 | 实测 | 说明 |
| --- | --- | --- |
| 条款级命中率 | 开发 100% / 验证 100% | 题集饱和，融合增益 0 不代表双路无价值 |
| 上下文召回率 | 100%（13/13） | 多条款标注题 |
| hard negatives 误报率 | 25%（2/8） | 基线；阈值维持 0.50 未调整 |
| 引用准确率 | 机制 100%（40/40）；存量 N=4 一致 | 存量样本不足，不宣称达到 95% 门槛 |
| 抽取回验通过率 | 100%（8/8） | 虚构样例，真实合同样本待补充 |
| 单元测试 | 128 例通过 | `mvn -o -B clean verify` |

## 测试

```powershell
# 单元测试
mvn -o -B test

# 完整构建（含单元测试）
mvn -o -B clean verify

# 容器化集成测试（Testcontainers）：需 Docker Desktop 暴露 2375
$env:DOCKER_HOST="tcp://localhost:2375"
mvn -o -B -pl agent-module -am test -Dcrag.it=true `
  -Dtest=IncrementalSyncContainerIT,RuleReplayIT `
  "-Dcrag.surefire.argLine=-Dapi.version=1.43"
```

Docker Engine ≥ 29 时需要 `-Dapi.version=1.43`，原因与排障步骤见 [`docs/ci.md`](docs/ci.md)。

## 目录结构

| 目录 | 内容 |
| --- | --- |
| `common-module/` | 公共内核：异常、上下文、切分、租户拦截、权限端口 |
| `doc-module/` | 合同上传、解析、切分、解析任务 |
| `kb-module/` | 知识库文档、版本、增量同步、召回 SQL |
| `retrieval-module/` | 双路召回、RRF、降级、缓存 |
| `audit-module/` | 审核任务、风险明细、复核、权限 |
| `agent-module/` | 规则、编排、模型、抽取、报告、SSE、启动类 |
| `contract-web/` | Vue 3 前端 |
| `db/init/`、`db/migration/` | 数据库初始化与 Flyway 迁移 |
| `docker/` | compose 与数据库镜像定义 |
| `eval/` | 知识库语料、示例合同、检索与抽取评估集 |
| `scripts/` | 环境启动、冒烟、评估、切分对比、CI 脚本 |
| `docs/` | 架构、数据字典、接口、运维 |

## 文档

| 文档 | 位置 |
| --- | --- |
| 架构说明 | [`docs/architecture.md`](docs/architecture.md) |
| 数据字典 | [`docs/db-dictionary.md`](docs/db-dictionary.md) |
| 接口文档 | [`docs/openapi.yaml`](docs/openapi.yaml) |
| 运维手册 | [`docs/ops.md`](docs/ops.md) |

## 已知限制

- **评估集饱和**：正例题集命中率接近 100%，融合增益无法体现，需补充更难的正例与真实脱敏合同；
- **忠实度与幻觉率未测**：需要人工抽检与 A/B 实验，当前不提供相关百分比；
- **OCR 未实现**：扫描件按规范返回 20005“请上传可编辑版本”；
- **DOCX 页数未知**：解析结果固定标记为未知，不冒充真实页数；
- **知识库只存正文**：不保留原始 PDF/DOC/DOCX 文件；
- **hard negatives 导出为手动触发**，尚未接入每日调度；
- **前端无自动化测试**，仅构建校验与人工验收。

## 安全与合规

- 密钥仅通过环境变量注入，仓库不包含任何真实密钥、合同或客户数据；
- 租户条件由 SQL 拦截器强制注入，跨租户资源统一返回“资源不存在”，避免枚举；
- 上传文件做扩展名 + 魔数 + OOXML 包校验，拒绝含宏文档，存储目录与静态资源分离；
- 报告与页面固定展示四条业务边界：不做法律意见、不替代法务决策、不承诺结论必然正确、不判断合同法律效力；
- 日志不打印合同全文、主体信息与密钥，模型请求响应截断脱敏。

## 开源说明

本项目以开源方式分享，欢迎 Issue、PR 与二次开发。

- 仅用于学习、研究与工程实践参考，不构成法律意见，也不对审核结论的正确性作任何保证；
- 请勿在仓库、Issue 或截图中提交真实合同、客户信息、密钥与内部制度；
- 二次开发或分发时，请保留项目来源、免责声明与四条业务边界；
- 用于生产环境前，请自行完成安全评估、合规审查与模型/数据授权确认；
- 当前仓库尚未声明开源许可证，正式开源前请补充 `LICENSE`（如 MIT / Apache-2.0）。

如果这个项目对您有帮助，欢迎 Star 支持。