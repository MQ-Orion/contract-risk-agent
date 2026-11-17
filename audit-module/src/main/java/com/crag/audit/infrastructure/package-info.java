/**
 * 审核模块（第 8–9 周里程碑）— infrastructure 层。
 *
 * <p>模块职责：任务状态机、风险明细、人工复核、回流导出、统计；禁止反向依赖 agent-module。</p>
 *
 * <p>层职责：基础设施层：Mapper、模型调用、缓存、文件读写；禁止承载业务规则。</p>
 *
 * <p>本轮仅建立五层包结构（规范 3.2），实现随对应里程碑落地；空层不得放置临时逻辑。</p>
 */
package com.crag.audit.infrastructure;