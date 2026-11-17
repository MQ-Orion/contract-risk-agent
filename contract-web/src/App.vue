<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import ContractPage from './pages/ContractPage.vue'
import KnowledgePage from './pages/KnowledgePage.vue'
import ReportPage from './pages/ReportPage.vue'
import ReviewPage from './pages/ReviewPage.vue'
import RulesPage from './pages/RulesPage.vue'
import { cancelTask, getRisks, getTask, latestTask, startAudit, subscribeProgress } from './api'

const navItems = [
  { key: 'contract', label: '合同管理', subtitle: '上传、解析、条款与发起初审' },
  { key: 'kb', label: '知识库', subtitle: '制度/模板管理、上下架与增量同步' },
  { key: 'audit', label: '审核任务', subtitle: '进度、风险清单、人工复核与报告导出' },
  { key: 'rules', label: '规则管理', subtitle: '规则配置、试算、启停与缓存刷新' }
]

const contractId = ref('')
const taskId = ref('')
const openTaskId = ref('')
const task = ref(null)
const risks = ref([])
const progress = ref({ status: '', percent: 0 })
const error = ref('')
const view = ref('contract')
const tab = ref('report')
const running = ref(false)
let unsubscribe = null

const currentNav = computed(() => navItems.find((item) => item.key === view.value) || navItems[0])

const counts = computed(() => {
  const result = { HIGH: 0, MEDIUM: 0, LOW: 0 }
  for (const risk of risks.value) {
    result[risk.level] = (result[risk.level] || 0) + 1
  }
  return result
})

const statusText = computed(() => {
  const map = {
    CREATED: '已创建',
    PARSING: '解析中',
    AUDITING: '审核中',
    PENDING_REVIEW: '待复核',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消'
  }
  return map[progress.value.status] || progress.value.status
})

async function loadTask(id) {
  task.value = await getTask(id)
  risks.value = await getRisks(id)
  // 百分比按任务状态回填：刷新/打开旧任务时不能沿用上一次任务的进度
  const percentByStatus = { CREATED: 5, AUDITING: 40, PENDING_REVIEW: 90, COMPLETED: 100 }
  progress.value = { status: task.value.status, percent: percentByStatus[task.value.status] ?? 0 }
}

function subscribe(id) {
  unsubscribe?.()
  unsubscribe = subscribeProgress(id, {
    progress: (event) => {
      progress.value = event
    },
    risk: async () => {
      await loadTask(id)
      tab.value = 'review'
    },
    complete: async (event) => {
      progress.value = { status: event.status, percent: 100 }
      await loadTask(id)
      running.value = false
    },
    error: (event) => {
      error.value = event.reason || '进度推送异常'
      running.value = false
    }
  })
}

async function start(targetContractId) {
  error.value = ''
  const id = (targetContractId || contractId.value).trim()
  if (!id) {
    error.value = '请输入合同标识，或先在“合同管理”上传合同'
    return
  }
  contractId.value = id
  try {
    running.value = true
    const result = await startAudit(id, false)
    taskId.value = result.taskId
    history.replaceState(null, '', `?taskId=${result.taskId}`)
    view.value = 'audit'
    tab.value = 'report'
    await loadTask(result.taskId)
    subscribe(result.taskId)
  } catch (e) {
    error.value = `${e.message}（${e.code ?? '未知'}）`
    running.value = false
  }
}

function startFromContract(id) {
  contractId.value = id
  view.value = 'audit'
  start(id)
}

async function openTask() {
  error.value = ''
  const id = openTaskId.value.trim()
  if (!id) {
    error.value = '请输入任务标识'
    return
  }
  try {
    taskId.value = id
    await loadTask(id)
    view.value = 'audit'
    if (!['COMPLETED', 'FAILED', 'CANCELLED'].includes(task.value.status)) {
      subscribe(id)
    }
  } catch (e) {
    error.value = `${e.message}（${e.code ?? '未知'}）`
  }
}

async function refresh() {
  error.value = ''
  const cid = contractId.value.trim()
  // 输入了合同标识且当前任务不是这个合同的：按合同查最近一次任务（换合同后刷新也能看到结果）
  if (cid && (!task.value || String(task.value.contractId) !== cid)) {
    try {
      const latest = await latestTask(cid)
      taskId.value = latest.taskId
      openTaskId.value = latest.taskId
      await loadTask(latest.taskId)
      if (!['COMPLETED', 'FAILED', 'CANCELLED'].includes(task.value.status)) {
        subscribe(latest.taskId)
      }
    } catch (e) {
      if (e.code === 10004) {
        resetWorkspace()
        contractId.value = cid
        error.value = '该合同还没有审核任务，请先点“发起审核”'
      } else {
        error.value = `${e.message}（${e.code ?? '未知'}）`
      }
    }
    return
  }
  // 只有任务标识：直接重新加载
  if (taskId.value) {
    try {
      await loadTask(taskId.value)
    } catch (e) {
      error.value = e.message
    }
    return
  }
  // 只有“打开已有任务”输入：按任务标识打开
  if (openTaskId.value.trim()) {
    await openTask()
    return
  }
  resetWorkspace()
  error.value = '当前没有打开的任务：可输入合同/任务标识，或到“合同管理”发起初审'
}

function resetWorkspace() {
  unsubscribe?.()
  taskId.value = ''
  openTaskId.value = ''
  task.value = null
  risks.value = []
  progress.value = { status: '', percent: 0 }
  running.value = false
  history.replaceState(null, '', location.pathname)
}

async function cancel() {
  if (!taskId.value) return
  error.value = ''
  try {
    await cancelTask(taskId.value, '前端手动作废')
    await loadTask(taskId.value)
    unsubscribe?.()
    running.value = false
  } catch (e) {
    error.value = `${e.message}（${e.code ?? '未知'}）`
  }
}

onMounted(async () => {
  const fromUrl = new URLSearchParams(location.search).get('taskId')
  if (fromUrl) {
    taskId.value = fromUrl
    openTaskId.value = fromUrl
    view.value = 'audit'
    try {
      await loadTask(fromUrl)
      // 断线续订：只按任务标识重新订阅，不重新创建任务（规范 10.6）
      if (task.value.status !== 'COMPLETED' && task.value.status !== 'FAILED' && task.value.status !== 'CANCELLED') {
        subscribe(fromUrl)
      }
    } catch (e) {
      error.value = e.message
    }
  }
})

onBeforeUnmount(() => unsubscribe?.())
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">CR</span>
        <div>
          <strong>合同风控</strong>
          <small>初审辅助平台</small>
        </div>
      </div>
      <nav class="side-nav">
        <button
          v-for="item in navItems"
          :key="item.key"
          :class="{ active: view === item.key }"
          @click="view = item.key"
        >
          {{ item.label }}
        </button>
      </nav>
      <div class="sidebar-footer">
        <div>租户 1 · 用户 1</div>
        <div class="muted">初审辅助，不构成法律意见</div>
      </div>
    </aside>

    <main class="workspace">
      <header class="workspace-head">
        <div>
          <h1>{{ currentNav.label }}</h1>
          <p class="muted">{{ currentNav.subtitle }}</p>
        </div>
        <span class="env-tag">开发环境</span>
      </header>

      <ContractPage v-if="view === 'contract'" @audit="startFromContract" />
      <KnowledgePage v-else-if="view === 'kb'" />
      <RulesPage v-else-if="view === 'rules'" />

      <template v-else>
        <section class="panel">
          <div class="row" style="justify-content: space-between">
            <div class="row">
              <label class="field">
                合同标识
                <input v-model="contractId" placeholder="上传后自动填入，也可粘贴合同 ID" />
              </label>
              <label class="field">
                打开已有任务
                <input v-model="openTaskId" placeholder="任务 ID" @keyup.enter="openTask" />
              </label>
              <button :disabled="running" @click="start()">{{ running ? '审核中…' : '发起审核' }}</button>
              <button class="secondary" :disabled="!openTaskId" @click="openTask">打开</button>
              <button class="secondary" :disabled="!taskId" @click="refresh">刷新</button>
              <button
                v-if="task && ['CREATED', 'AUDITING', 'PENDING_REVIEW'].includes(task.status)"
                class="secondary danger"
                @click="cancel"
              >
                取消任务
              </button>
            </div>
          </div>
          <p v-if="error" class="error">{{ error }}</p>
          <div v-if="progress.status" class="progress-block">
            <div class="row" style="justify-content: space-between">
              <span class="muted">
                任务 {{ taskId }} · {{ statusText }} · 高 {{ counts.HIGH }} / 中 {{ counts.MEDIUM }} / 低 {{ counts.LOW }}
              </span>
              <span class="muted">{{ progress.percent }}%</span>
            </div>
            <div class="progress"><span :style="{ width: `${progress.percent}%` }"></span></div>
          </div>
        </section>

        <nav class="tabs">
          <button :class="{ active: tab === 'report' }" @click="tab = 'report'">报告页</button>
          <button :class="{ active: tab === 'review' }" @click="tab = 'review'">复核页</button>
        </nav>

        <ReportPage v-if="tab === 'report'" :task="task" :risks="risks" :counts="counts" />
        <ReviewPage
          v-else
          :task="task"
          :risks="risks"
          @changed="refresh"
          @error="(message) => (error = message)"
        />
      </template>
    </main>
  </div>
</template>
