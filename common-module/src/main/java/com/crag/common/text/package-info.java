/**
 * 条款切分共享内核：切分算法、切分参数、条款类型判定与相关值对象。
 *
 * <p>下沉到基础组件是为同时满足规范 5.4（知识库与合同必须使用完全相同的切分实现）
 * 与 2.2（kb-module 只允许依赖 common-module，不允许依赖 doc-module）。决策记录见 ADR 0003。</p>
 */
package com.crag.common.text;