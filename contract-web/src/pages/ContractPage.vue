<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  deleteContract,
  getClauses,
  getContract,
  getParseTask,
  listContracts,
  retryParse,
  uploadContract
} from '../api'

const emit = defineEmits(['audit'])

const file = ref(null)
const contract = ref(null)
const parseTask = ref(null)
const clauses = ref([])
const error = ref('')
const uploading = ref(false)
const detailOpen = ref(false)
const pollTimer = ref(null)

const contracts = ref([])
const total = ref(0)
const listLoading = ref(false)
const listError = ref('')

const parseStatusText = computed(() => {
  const map = {
    PENDING: '排队中',
    PARSING: '解析中',
    SPLITTING: '切分中',
    EMBEDDING: '向量化中',
    READY: '就绪',
    FAILED: '失败'
  }
  return map[parseTask.value?.status] || parseTask.value?.status || ''
})

const ready = computed(() => parseTask.value?.status === 'READY')

const formatText = { DOCX: 'Word 文档', DOC: 'Word 文档', PDF: 'PDF' }
const clauseTypeText = {
  PAYMENT: '付款',
  BREACH: '违约',
  CONFIDENTIALITY: '保密',
  JURISDICTION: '争议管辖',
  DISCLAIMER: '免责',
  OTHER: '其他'
}
const typeSourceText = {
  KEYWORD: '关键词',
  DEFAULT: '默认',
  MODEL: '模型判定'
}

const auditStatusText = {
  UNREVIEWED: '未审核',
  CREATED: '已创建',
  PARSING: '审核中',
  AUDITING: '审核中',
  PENDING_REVIEW: '待复核',
  COMPLETED: '审核完成',
  FAILED: '审核失败',
  CANCELLED: '已取消'
}

const auditStatusClass = {
  UNREVIEWED: 'tag-off',
  AUDITING: 'tag-auditing',
  PENDING_REVIEW: 'tag-pending',
  COMPLETED: 'tag-on',
  FAILED: 'tag-failed',
  CANCELLED: 'tag-off'
}

function onFileChange(event) {
  file.value = event.target.files?.[0] || null
}

async function loadContracts() {
  listLoading.value = true
  listError.value = ''
  try {
    const page = await listContracts(1, 50)
    contracts.value = page.items
    total.value = page.total
  } catch (e) {
    listError.value = e.message
  } finally {
    listLoading.value = false
  }
}

async function submit() {
  error.value = ''
  if (!file.value) {
    error.value = '请选择 PDF / DOC / DOCX 文件'
    return
  }
  uploading.value = true
  try {
    const result = await uploadContract(file.value)
    contract.value = await getContract(result.contractId)
    detailOpen.value = true
    await refreshParse(result.contractId)
    poll(result.contractId)
    await loadContracts()
  } catch (e) {
    error.value = `${e.message}（${e.code ?? '未知'}）`
  } finally {
    uploading.value = false
  }
}

async function refreshParse(contractId) {
  parseTask.value = await getParseTask(contractId)
  if (parseTask.value.status === 'READY') {
    clauses.value = await getClauses(contractId)
  }
}

function poll(contractId) {
  stopPoll()
  pollTimer.value = setInterval(async () => {
    try {
      await refreshParse(contractId)
      if (['READY', 'FAILED'].includes(parseTask.value.status)) {
        stopPoll()
        await loadContracts()
      }
    } catch (e) {
      error.value = e.message
      stopPoll()
    }
  }, 2000)
}

function stopPoll() {
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
}

async function retry() {
  try {
    parseTask.value = await retryParse(contract.value.contractId)
    poll(contract.value.contractId)
  } catch (e) {
    error.value = e.message
  }
}

function startAudit(contractId) {
  emit('audit', contractId)
}

function openContract(row) {
  // 再次点击“查看”时收起，避免详情面板一直占着页面
  if (detailOpen.value && contract.value?.contractId === row.contractId) {
    closeDetail()
    return
  }
  contract.value = { contractId: row.contractId, name: row.name, format: row.format, fileSize: row.fileSize }
  clauses.value = []
  parseTask.value = { status: 'READY' }
  detailOpen.value = true
  refreshParse(row.contractId).catch((e) => (error.value = e.message))
}

function closeDetail() {
  detailOpen.value = false
  contract.value = null
  clauses.value = []
  parseTask.value = null
}

async function removeContract(row) {
  if (!window.confirm(`确认删除合同「${row.name}」？删除后合同与条款不再可见，审核历史保留。`)) {
    return
  }
  listError.value = ''
  try {
    await deleteContract(row.contractId)
    if (contract.value?.contractId === row.contractId) {
      closeDetail()
    }
    await loadContracts()
  } catch (e) {
    listError.value = `${e.message}（${e.code ?? '未知'}）`
  }
}

onMounted(loadContracts)
onBeforeUnmount(stopPoll)
</script>

<template>
  <section class="panel">
    <div class="row" style="justify-content: space-between">
      <h2 class="panel-title">合同上传与解析</h2>
      <button v-if="contract && detailOpen" class="secondary" @click="closeDetail">收起详情</button>
    </div>
    <div class="row">
      <label class="field" style="flex: 1">
        选择合同文件（PDF / DOC / DOCX，≤50MB）
        <input type="file" accept=".pdf,.doc,.docx" @change="onFileChange" />
      </label>
      <button :disabled="uploading" @click="submit">{{ uploading ? '上传中…' : '上传并解析' }}</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <template v-if="contract && detailOpen">
      <table style="margin-top: 12px">
        <tbody>
          <tr>
            <th style="width: 120px">合同标识</th>
            <td>{{ contract.contractId }}</td>
            <th style="width: 80px">名称</th>
            <td>{{ contract.name }}</td>
          </tr>
          <tr>
            <th>格式 / 大小</th>
            <td>{{ formatText[contract.format] || contract.format }} / {{ Math.round((contract.fileSize || 0) / 1024) }} KB</td>
            <th>解析状态</th>
            <td>
              {{ parseStatusText }}
              <span v-if="parseTask?.status === 'FAILED'" class="error">
                （{{ parseTask.errorCode }}）
                <button class="secondary" style="margin-left: 8px" @click="retry">重试</button>
              </span>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="ready" class="row" style="margin-top: 12px">
        <button @click="startAudit(contract.contractId)">发起初审</button>
        <span class="muted">共 {{ clauses.length }} 个条款切片</span>
      </div>

      <table v-if="clauses.length" style="margin-top: 12px">
        <thead>
          <tr>
            <th style="width: 90px">条款</th>
            <th style="width: 120px">类型</th>
            <th style="width: 100px">来源</th>
            <th>文本</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="clause in clauses" :key="clause.fullTextIndex">
            <td>{{ clause.clauseNo }}</td>
            <td>{{ clauseTypeText[clause.clauseType] || clause.clauseType }}</td>
            <td class="muted">{{ typeSourceText[clause.typeSource] || clause.typeSource }}</td>
            <td>{{ clause.text }}</td>
          </tr>
        </tbody>
      </table>
    </template>
  </section>

  <section class="panel">
    <div class="row" style="justify-content: space-between">
      <div>
        <h2 class="panel-title">本租户合同（{{ total }}）</h2>
        <p class="muted" style="margin: 0">仅显示当前租户；点击“审核”可直接发起初审，任意状态均可删除。</p>
      </div>
      <button class="secondary" :disabled="listLoading" @click="loadContracts">
        {{ listLoading ? '刷新中…' : '刷新' }}
      </button>
    </div>
    <p v-if="listError" class="error">{{ listError }}</p>

    <table style="margin-top: 12px">
      <thead>
        <tr>
          <th style="width: 90px">审核状态</th>
          <th style="width: 210px">合同 ID（雪花）</th>
          <th>合同名称</th>
          <th style="width: 90px">格式</th>
          <th style="width: 90px">大小</th>
          <th style="width: 160px">上传时间</th>
          <th style="width: 230px">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in contracts" :key="row.contractId">
          <td>
            <span :class="auditStatusClass[row.auditStatus] || 'tag-off'">
               {{ auditStatusText[row.auditStatus] || row.auditStatus }}
            </span>
          </td>
          <td><code>{{ row.contractId }}</code></td>
          <td>{{ row.name }}</td>
          <td class="muted">{{ formatText[row.format] || row.format }}</td>
          <td class="muted">{{ Math.round((row.fileSize || 0) / 1024) }} KB</td>
          <td class="muted">{{ (row.createdAt || '').replace('T', ' ').slice(0, 19) }}</td>
          <td class="actions-cell">
            <button class="secondary" @click="startAudit(row.contractId)">审核</button>
            <button class="secondary" @click="openContract(row)">
              {{ detailOpen && contract?.contractId === row.contractId ? '收起' : '查看' }}
            </button>
            <button class="secondary danger" @click="removeContract(row)">删除</button>
          </td>
        </tr>
        <tr v-if="contracts.length === 0">
          <td colspan="7" class="muted">暂无合同，先在上方上传。</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
