#!/usr/bin/env bash
# CI 统一入口（平台无关）：构建 + 单元测试 + 容器化集成测试。
# 容器化测试通过 Testcontainers 执行；本机需开启 Docker Desktop 的 tcp://localhost:2375（ADR 0002 偏差 13 已关闭）。
#
# 用法（本地或任意 CI runner）：
#   bash scripts/ci-verify.sh
#
# 依赖：JDK 17、Maven 3.9、Docker（含 Testcontainers 所需权限）
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "== 1/3 构建与单元测试 =="
mvn -B -ntp clean verify

echo "== 2/3 检查 Docker 可用性 =="
if ! docker info >/dev/null 2>&1; then
    echo "Docker 不可用：跳过容器化集成测试（需开启 Docker Desktop TCP 端点，见 docs/ci.md）"
    exit 0
fi

echo "== 3/3 容器化集成测试（增量向量写入） =="
# 说明：TenantIsolationIT 依赖外部数据库，不在默认流水线执行；
# 需要时在具备 DB/Redis 的专用环境运行，并注入 DASHSCOPE_API_KEY。
mvn -B -ntp -pl agent-module -am test \
    -Dcrag.it=true \
    -Dtest=IncrementalSyncContainerIT,RuleReplayIT \
    "-Dsurefire.failIfNoSpecifiedTests=false" \
    "-Dcrag.surefire.argLine=-Dapi.version=1.43"

echo "CI 验证通过"
