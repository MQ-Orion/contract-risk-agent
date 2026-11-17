<script setup>
import { computed, reactive, ref } from 'vue'
import { completeTask, exportHardNegatives, reviewRisk } from '../api'

const props = defineProps({
  task: { type: Object, default: null },
  risks: { type: Array, default: () => [] }
})
const emit = defineEmits(['changed', 'error'])

const reasons = [
  { code: 'IRRELEVANT_RETRIEVAL', label: '检索无关' },
  { code: 'WRONG_CITATION', label: '引用错误' },
  { code: 'RULE_FALSE_POSITIVE', label: '规则误报' },
  { code: 'SEMANTIC_MISJUDGE', label: '语义误判' }
]
const forms = reactive({})
const conclusion = ref('')
const busy = ref(false)
const exportInfo = ref('')
const levelLabel = { HIGH: '高', MEDIUM: '中', LOW: '低' }
const statusLabel = {
  CREATED: '已创建',
  PARSING: '解析中',
  AUDITING: '审核中',
  PENDING_REVIEW: '待复核',
  COMPLETED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消'
}

const reviewable = computed(() => props.task?.status === 'PENDING_REVIEW')
const pendingHigh = computed(
  () => props.risks.filter((risk) => !risk.reviewed && (risk.level === 'HIGH' || risk.level === 'MEDIUM')).length
)

function formOf(risk) {
  if (!forms[risk.id]) {
    forms[risk.id] = { action: 'PASS', reasonCode: '', comment: '' }
  }
  return forms[risk.id]
}

async function submit(risk) {
  const form = formOf(risk)
  if (form.action === 'REJECT' && !form.reasonCode) {
    emit('error', '驳回必须选择原因码')
    return
  }
  busy.value = true
  try {
    await reviewRisk(props.task.id, risk.id, {
      action: form.action,
      reasonCode: form.action === 'REJECT' ? form.reasonCode : null,
      comment: form.comment,
      // 乐观锁：回传当前版本，服务端以 version_no 条件更新（规范 10.7）
      versionNo: risk.versionNo
    })
    emit('changed')
  } catch (e) {
    emit('error', `${e.message}（${e.code ?? '未知'}）`)
  } finally {
    busy.value = false
  }
}

async function exportSamples() {
  busy.value = true
  exportInfo.value = ''
  try {
    const result = await exportHardNegatives()
    exportInfo.value = `已导出 ${result.total} 条：${result.filePath}`
  } catch (e) {
    emit('error', `${e.message}（${e.code ?? '未知'}）`)
  } finally {
    busy.value = false
  }
}

async function finish() {
  busy.value = true
  try {
    await completeTask(props.task.id, conclusion.value)
    emit('changed')
  } catch (e) {
    emit('error', `${e.message}（${e.code ?? '未知'}）`)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="panel">
    <div v-if="!task" class="muted">发起审核后在此复核风险项。</div>
    <template v-else>
      <div class="row" style="justify-content: space-between">
        <p class="muted" v-if="!reviewable">当前状态：{{ statusLabel[task.status] || task.status }}；只有待复核状态可执行复核。</p>
        <p v-else class="muted">未复核的高/中风险：{{ pendingHigh }} 条；全部复核后才能完成任务。</p>
        <button class="secondary" :disabled="busy" @click="exportSamples">导出驳回样本</button>
      </div>
      <p v-if="exportInfo" class="muted">{{ exportInfo }}</p>

      <table>
        <thead>
          <tr>
            <th style="width: 56px">等级</th>
            <th style="width: 100px">条款</th>
            <th>风险说明</th>
            <th style="width: 150px">复核动作</th>
            <th style="width: 300px">原因与备注</th>
            <th style="width: 90px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="risk in risks" :key="risk.id">
            <td><span class="badge" :class="risk.level">{{ levelLabel[risk.level] || risk.level }}</span></td>
            <td>{{ risk.clauseNo }}</td>
            <td>
              {{ risk.content }}
              <div class="muted">{{ risk.sourceExcerpt }}</div>
            </td>
            <td>
              <select v-model="formOf(risk).action" :disabled="!reviewable || risk.reviewed">
                <option value="PASS">通过</option>
                <option value="REJECT">驳回</option>
                <option value="NEED_FIX">待修改</option>
              </select>
            </td>
            <td>
              <select
                class="reason-select"
                v-model="formOf(risk).reasonCode"
                :disabled="!reviewable || risk.reviewed || formOf(risk).action !== 'REJECT'"
              >
                <option value="">（驳回必选原因码）</option>
                <option v-for="reason in reasons" :key="reason.code" :value="reason.code">{{ reason.label }}</option>
              </select>
              <input
                v-model="formOf(risk).comment"
                placeholder="备注（可选）"
                :disabled="!reviewable || risk.reviewed"
                style="margin-top: 6px; width: 100%"
              />
            </td>
            <td>
              <button v-if="!risk.reviewed" :disabled="!reviewable || busy" @click="submit(risk)">提交</button>
              <span v-else class="muted">{{ risk.reviewResult === 'PASS' ? '已通过' : risk.reviewResult === 'REJECT' ? '已驳回' : '待修改' }}</span>
            </td>
          </tr>
          <tr v-if="risks.length === 0">
            <td colspan="6" class="muted">暂无风险项。</td>
          </tr>
        </tbody>
      </table>

      <div class="row" style="margin-top: 16px">
        <label class="field" style="flex: 1">
          人工结论
          <input v-model="conclusion" placeholder="例如：风险可接受，准予签署" :disabled="!reviewable" />
        </label>
        <button :disabled="!reviewable || busy || pendingHigh > 0" @click="finish">完成审核</button>
      </div>

      <div class="disclaimer">
        <div>复核动作三类：通过、驳回、待修改；驳回原因码固定四类（检索无关、引用错误、规则误报、语义误判）。</div>
        <strong>本页结论由人工复核产生，系统辅助生成的风险项仅作初审参考，不构成法律意见。</strong>
      </div>
    </template>
  </section>
</template>
