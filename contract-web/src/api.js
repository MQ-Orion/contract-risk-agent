/**
 * 后端接口封装：统一响应体为 {code, message, data, traceId, timestamp}，
 * 这里只把 data 交给页面，错误统一抛出，页面负责提示（规范 10.2）。
 */
async function request(path, options = {}) {
  const isForm = options.body instanceof FormData
  const response = await fetch(path, {
    ...options,
    headers: isForm ? {} : { 'Content-Type': 'application/json', ...(options.headers || {}) }
  })
  const body = await response.json().catch(() => ({ code: -1, message: '响应不是合法 JSON' }))
  if (body.code !== 0) {
    const error = new Error(body.message || '请求失败')
    error.code = body.code
    error.traceId = body.traceId
    throw error
  }
  return body.data
}

/* ---------------- 合同 ---------------- */

export function uploadContract(file) {
  const form = new FormData()
  form.append('file', file)
  return request('/api/v1/contracts', { method: 'POST', body: form })
}

export function getContract(contractId) {
  return request(`/api/v1/contracts/${contractId}`)
}

export function getParseTask(contractId) {
  return request(`/api/v1/contracts/${contractId}/parse-task`)
}

export function retryParse(contractId) {
  return request(`/api/v1/contracts/${contractId}/parse-task/retry`, { method: 'POST' })
}

export function getClauses(contractId) {
  return request(`/api/v1/contracts/${contractId}/clauses`)
}

export function listContracts(page = 1, size = 50) {
  return request(`/api/v1/contracts?page=${page}&size=${size}`)
}

export function deleteContract(contractId) {
  return request(`/api/v1/contracts/${contractId}`, { method: 'DELETE' })
}

/* ---------------- 知识库 ---------------- */

export function listKbDocs(active) {
  const query = active === undefined || active === null ? '' : `?active=${active}`
  return request(`/api/v1/kb/documents${query}`)
}

export function uploadKbDocument(file, payload = {}) {
  const form = new FormData()
  form.append('file', file)
  if (payload.title) form.append('title', payload.title)
  form.append('docType', payload.docType || 'INSTITUTION')
  form.append('activate', String(payload.activate ?? true))
  return request('/api/v1/kb/documents/upload', { method: 'POST', body: form })
}

export function createKbDoc(payload) {
  return request('/api/v1/kb/documents', { method: 'POST', body: JSON.stringify(payload) })
}

export function updateKbContent(docId, content) {
  return request(`/api/v1/kb/documents/${docId}/content`, {
    method: 'PUT',
    body: JSON.stringify({ content })
  })
}

export function publishKb(docId) {
  return request(`/api/v1/kb/documents/${docId}/publish`, { method: 'POST' })
}

export function unpublishKb(docId) {
  return request(`/api/v1/kb/documents/${docId}/unpublish`, { method: 'POST' })
}

export function syncKb(docId) {
  return request(`/api/v1/kb/documents/${docId}/sync`, { method: 'POST' })
}

/* ---------------- 审核 ---------------- */

export function startAudit(contractId, rulesOnly = false) {
  return request(`/api/v1/audit/tasks/run?contractId=${encodeURIComponent(contractId)}&rulesOnly=${rulesOnly}`, {
    method: 'POST'
  })
}

export function latestTask(contractId) {
  return request(`/api/v1/audit/tasks/latest?contractId=${encodeURIComponent(contractId)}`)
}

export function getTask(taskId) {
  return request(`/api/v1/audit/tasks/${taskId}`)
}

export function getRisks(taskId) {
  return request(`/api/v1/audit/tasks/${taskId}/risks`)
}

export function reviewRisk(taskId, riskId, payload) {
  return request(`/api/v1/audit/tasks/${taskId}/risks/${riskId}/review`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function completeTask(taskId, conclusion) {
  return request(`/api/v1/audit/tasks/${taskId}/complete`, {
    method: 'POST',
    body: JSON.stringify({ conclusion })
  })
}

export function exportHardNegatives() {
  return request('/api/v1/audit/hard-negatives/export', { method: 'POST' })
}

export function cancelTask(taskId, reason) {
  return request(`/api/v1/audit/tasks/${taskId}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  })
}

export function previewReview(contractId, rulesOnly = false) {
  return request(`/api/v1/contracts/${contractId}/review?rulesOnly=${rulesOnly}`, { method: 'POST' })
}

/* ---------------- 规则 ---------------- */

export function listRules(includeDisabled = true) {
  return request(`/api/v1/rules?includeDisabled=${includeDisabled}`)
}

export function createRule(payload) {
  return request('/api/v1/rules', { method: 'POST', body: JSON.stringify(payload) })
}

export function updateRule(ruleId, payload) {
  return request(`/api/v1/rules/${ruleId}`, { method: 'PUT', body: JSON.stringify(payload) })
}

export function setRuleEnabled(ruleId, enabled) {
  return request(`/api/v1/rules/${ruleId}/${enabled ? 'enable' : 'disable'}`, { method: 'POST' })
}

export function dryRunRule(ruleId, payload) {
  return request(`/api/v1/rules/${ruleId}/dry-run`, { method: 'POST', body: JSON.stringify(payload) })
}

export function refreshRules() {
  return request('/api/v1/rules/refresh', { method: 'POST' })
}

/**
 * 报告下载地址：后端以附件流返回 DOCX（直接跳转即可，浏览器会保留文件名）。
 *
 * @param {string} taskId 任务标识
 * @returns {string} 下载 URL
 */
export function reportDownloadUrl(taskId) {
  return `/api/v1/audit/tasks/${taskId}/report`
}

/**
 * 订阅任务进度（规范 10.6）：只按任务标识续订，不创建任务；断线后有限次重连。
 *
 * @param {string} taskId 任务标识
 * @param {object} handlers 事件回调：progress/risk/complete/error
 * @returns {Function} 取消订阅函数
 */
export function subscribeProgress(taskId, handlers) {
  let source = null
  let retries = 0
  let closed = false

  const connect = () => {
    if (closed) return
    source = new EventSource(`/api/v1/audit/tasks/${taskId}/stream`)
    source.addEventListener('progress', (event) => handlers.progress?.(JSON.parse(event.data)))
    source.addEventListener('risk', (event) => handlers.risk?.(JSON.parse(event.data)))
    source.addEventListener('complete', (event) => {
      handlers.complete?.(JSON.parse(event.data))
      close()
    })
    source.addEventListener('error', (event) => {
      // 服务端发送的 error 事件带 data；连接层错误没有 data，按断线重连处理
      if (event.data) {
        handlers.error?.(JSON.parse(event.data))
        close()
        return
      }
      source.close()
      if (retries >= 3) {
        handlers.error?.({ status: 'DISCONNECTED', reason: '进度连接中断，请刷新页面按任务标识续订' })
        return
      }
      retries += 1
      setTimeout(connect, 3000)
    })
  }

  const close = () => {
    closed = true
    source?.close()
  }

  connect()
  return close
}
