# 持续集成约定

## 1. 流水线内容

平台无关的统一入口：`scripts/ci-verify.sh`

| 阶段 | 命令 | 说明 |
| --- | --- | --- |
| 构建与单测 | `mvn -B -ntp clean verify` | 7 模块单测；不依赖外部服务 |
| Docker 检查 | `docker info` | 不可用时跳过后续阶段；本机需开启 Docker Desktop 的 TCP 端点（见第 2 节） |
| 容器化集成测试 | `mvn -B -ntp -pl agent-module -am test -Dcrag.it=true -Dtest=IncrementalSyncContainerIT` | 用 `crag-postgres:16-zhparser` 与 `redis:8` 临时容器验证增量写入与版本切换语义 |

## 2. 本机执行容器化测试

Docker Desktop 默认只暴露命名管道，docker-java 连接会返回 HTTP 400。按以下步骤改为 TCP：

1. Docker Desktop → Settings → General → 勾选 **Expose daemon on tcp://localhost:2375 without TLS**；
2. 重新启动 Docker Desktop；
3. 执行：

```powershell
$env:DOCKER_HOST = 'tcp://localhost:2375'
$env:TESTCONTAINERS_RYUK_DISABLED = 'true'   # 可选：避免 Ryuk 在 Windows 上额外拉容器
mvn -o -B -pl agent-module -am test "-Dcrag.it=true" "-Dtest=IncrementalSyncContainerIT" `
  "-Dsurefire.failIfNoSpecifiedTests=false" "-Dcrag.surefire.argLine=-Dapi.version=1.43"
```

**为什么需要 `-Dapi.version=1.43`**：本机 Docker Engine 29 的最低 API 版本是 1.40，而 docker-java 3.3.x
默认按 1.32 发请求，`/v1.32/info` 被引擎直接拒绝（HTTP 400），表现为
`Could not find a valid Docker environment`。把 API 版本抬到引擎支持区间（≥1.40、≤引擎版本）即可。
该参数通过聚合 POM 的 `crag.surefire.argLine` 占位传入，不改变其他测试的 JVM 参数。

**为什么需要 `DOCKER_HOST`**：Testcontainers 通过环境变量发现 Docker；不设置会退回默认命名管道，
同样得到 400。

4. 通过后把结果补进 `docs/compliance/week5-7.md`（偏差 13 的关闭证据）。

> 安全提示：2375 为**无 TLS** 端口，仅在本机开发时开启，用完关闭；不要暴露到局域网或公网。

## 3. Runner 要求

- JDK 17（`JAVA_HOME` 指向 17）；
- Maven 3.9+；
- Docker daemon 可用，且 Testcontainers 能访问（Linux runner 直接可用；Windows runner 需 TCP 端点）；
- 首次构建需要访问 Maven 中央仓（CI 环境正常联网）；
- 不需要真实模型密钥：容器化测试用确定性向量替身；
- 若 runner 的 Docker Engine ≥ 29，同样需要传 `-Dcrag.surefire.argLine=-Dapi.version=<引擎支持的版本>`。

## 4. 缓存与产物

- 缓存 `~/.m2/repository`，key 建议取 `pom.xml` 的哈希；
- 归档 `agent-module/target/surefire-reports/` 与 `doc-module/target/surefire-reports/`；
- 容器化测试日志在失败时保留（Testcontainers 默认输出够用）。

## 5. 不进默认流水线的测试

| 测试 | 原因 | 触发方式 |
| --- | --- | --- |
| `RetrievalEvalIT` | 调用真实百炼 embedding，产生费用且依赖密钥 | 每周或里程碑结束手动触发，需注入 `DASHSCOPE_API_KEY`，报告归档 `docs/eval/` |
| `TenantIsolationIT` | 依赖外部 PostgreSQL/Redis（非容器） | 在具备依赖环境的专用任务中执行；后续可改为 Testcontainers |
| 模型真实调用用例 | 非确定性、产生费用（规范 13.2） | 夜间或里程碑手动触发，CI 默认使用固定响应替身 |

## 6. 平台接入

仓库尚未配置远程与 CI 平台。确定平台（Gitee Go / GitHub Actions / GitLab CI）后，只需新增一个调用
`bash scripts/ci-verify.sh` 的流水线文件，无需改动脚本与构建。

## 7. 质量门槛（规范 12.5）

| 门槛 | 当前 | 计划 |
| --- | --- | --- |
| 单测全部通过 | ✅ 见 `docs/PROGRESS.md` 第 3 节 | 持续 |
| 核心类覆盖率 ≥60% | ⬜ JaCoCo 接入中 | 随 CI 接入并设阈值 |
| 引用校验 ≥95% | 第 5–7 周实现 | 报告里程碑 |
| 无未关闭严重缺陷 | ✅ | 持续 |
| 文档同步 | ✅ | 每个 PR |
