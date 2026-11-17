# 本地依赖环境

本目录只负责依赖（数据库与缓存），符合规范 15.1「依赖以容器编排一键启动，环境必须可从零重建」。

## 数据库镜像

`postgres/Dockerfile` 在 `pgvector/pgvector:pg16` 之上编译安装 **SCWS 1.2.3 + zhparser 2.4**，
产出 `crag-postgres:16-zhparser`，供 `crag_cn` 中文分词配置使用（规范 6.5）。
首次 `docker compose up -d` 会自动构建，也可单独执行：

```powershell
docker compose -f compose.yaml build postgres
```

## 一键启动

```powershell
cd docker
Copy-Item .env.example .env
docker compose up -d
docker compose ps
```

推荐直接用仓库脚本（自动创建 `.env`、等待健康检查并执行自检）：

```powershell
pwsh -File scripts/dev-up.ps1
```

首次启动时，`db/init` 下的脚本会以超级用户身份依次执行：

| 脚本 | 作用 |
| --- | --- |
| `00_extensions.sql` | 安装 pgvector（缺失即失败，不带着“看似正常”的库上线） |
| `10_tsearch_cn.sql` | 创建中文分词配置 `crag_cn`：优先 zhparser / pg_jieba，缺失时退化为 simple 并打印告警 |
| `20_functions.sql` | 创建 tsv 维护函数 `crag_set_tsv()`，供切片表触发器调用 |

## 环境自检

```powershell
Get-Content ../db/verify/verify_week1_2.sql -Raw |
    docker exec -i crag-postgres psql -U crag -d contract_risk
```

输出中 `all_pass = t` 表示扩展、分词配置、函数、三张表、三个索引与触发器全部就绪。

## 表结构迁移

`db/migration` 下的脚本由应用启动时的 Flyway 执行（第 3–4 周接入 Flyway 10.x）。
在此之前，本地如需建表，可手工执行一次：

```powershell
Get-Content ../db/migration/V202611121010__init_doc_module_tables.sql -Raw |
    docker exec -i crag-postgres psql -U crag -d contract_risk -v ON_ERROR_STOP=1
```

## 已知环境约束

- 数据库镜像由本仓库构建（`postgres/Dockerfile`），已包含 pgvector 0.8 与 zhparser 2.4；若直接使用官方
  `pgvector/pgvector:pg16` 镜像，`crag_cn` 会退化为 simple，中文关键词召回不可用。
- 端口只绑定 `127.0.0.1`，不对外暴露；生产部署的鉴权、TLS 与镜像摘要固定随第 12 周发布里程碑补齐。
