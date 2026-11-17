# contract-web（Vue 3 前端）

报告页与复核页（规范 9.3、9.4、10.6）。Vue 3.4 + Vite 5，不引入路由与状态管理框架，
两个视图通过顶栏切换；所有数据经 REST，进度经 SSE。

## 页面与约束

| 视图 | 内容 | 约束 |
| --- | --- | --- |
| 报告页 | 任务元信息（模型/Prompt 版本、报告位置）、风险汇总、逐条风险与来源引用 | 页脚固定四条禁止 + 「本报告由系统辅助生成，仅作初审参考，不构成法律意见」 |
| 复核页 | 风险清单、通过/驳回/待修改、四类驳回原因码、备注、完成审核 | 驳回必须选原因码；提交携带 `versionNo` 走乐观锁；未复核高/中风险时禁用完成 |

进度：`GET /api/v1/audit/tasks/{taskId}/stream` 的 `progress/risk/complete/error` 事件；
断线后按任务标识重连（最多 3 次），不重新创建任务；最终结论以完成事件的报告为准。

## 本地运行

```powershell
# 依赖（首次）
pnpm install            # 或 npm install
# 开发（5173，/api 代理到 127.0.0.1:8080）
pnpm dev
# 构建静态产物（dist/，交由网关同源部署）
pnpm build
```

> 构建期 esbuild 需要启动子进程；若在受限沙箱中执行出现 `spawn EPERM`，在具备正常进程权限的环境构建即可。
> 生产构建产物不入仓（`.gitignore` 已排除 node_modules 与 dist）。

## 与后端约定

- 统一响应体 `{code, message, data, traceId, timestamp}`，`code !== 0` 视为失败；
- 发起审核：`POST /api/v1/audit/tasks/run?contractId=...`（幂等，返回 `taskId` 与 `streamUrl`）；
- 复核：`POST /api/v1/audit/tasks/{taskId}/risks/{riskId}/review`，请求体 `{action, reasonCode, comment, versionNo}`；
- 完成：`POST /api/v1/audit/tasks/{taskId}/complete`，请求体 `{conclusion}`。