/**
 * 检索模块（第 3–4 周里程碑）— infrastructure 层。
 *
 * <p>模块职责：双路召回、RRF 融合、精度优先与降级、检索缓存；对外唯一出口为 RetrievalService，禁止被绕过直连 SQL。</p>
 *
 * <p>层职责：基础设施层：Mapper、模型调用、缓存、文件读写；禁止承载业务规则。</p>
 *
 * <p>本轮仅建立五层包结构（规范 3.2），实现随对应里程碑落地；空层不得放置临时逻辑。</p>
 */
package com.crag.retrieval.infrastructure;