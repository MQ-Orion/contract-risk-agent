<script setup>
import { computed } from 'vue'
import { reportDownloadUrl } from '../api'

const props = defineProps({
  task: { type: Object, default: null },
  risks: { type: Array, default: () => [] },
  counts: { type: Object, required: true }
})

const disclaimer = '本报告由系统辅助生成，仅作初审参考，不构成法律意见。'
const prohibitions = [
  '不做法律意见。',
  '不替代法务决策。',
  '不承诺审核结论必然正确。',
  '不对合同的法律效力作出判断。'
]
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
const sourceLabel = { RULE: '规则', MODEL: '模型', RULE_CONFLICT: '规则与模型冲突' }
const rows = computed(() => props.risks)
</script>

<template>
  <section class="panel">
    <div v-if="!task" class="muted">发起审核后在此查看报告。</div>
    <template v-else>
      <table>
        <tbody>
          <tr>
            <th style="width: 120px">合同标识</th>
            <td>{{ task.contractId }}</td>
            <th style="width: 100px">状态</th>
            <td>{{ statusLabel[task.status] || task.status }}</td>
          </tr>
          <tr>
            <th>模型版本</th>
            <td>{{ task.modelVersion || '—' }}</td>
            <th>Prompt 版本</th>
            <td>{{ task.promptVersion || '—' }}</td>
          </tr>
          <tr>
            <th>报告文件</th>
            <td colspan="3">
              {{ task.reportUrl || '尚未导出' }}
              <a v-if="task.reportUrl" :href="reportDownloadUrl(task.id)" class="download-link">下载 DOCX</a>
            </td>
          </tr>
          <tr>
            <th>人工结论</th>
            <td colspan="3">{{ task.conclusion || '待复核' }}</td>
          </tr>
        </tbody>
      </table>

      <p class="muted" style="margin-top: 12px">
        风险汇总：高 {{ counts.HIGH }} 条，中 {{ counts.MEDIUM }} 条，低 {{ counts.LOW }} 条；高/中风险须人工复核后方可出具结论。
      </p>

      <table style="margin-top: 8px">
        <thead>
          <tr>
            <th style="width: 56px">等级</th>
            <th style="width: 110px">条款</th>
            <th>风险说明</th>
            <th style="width: 220px">来源与引用</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="risk in rows" :key="risk.id">
            <td><span class="badge" :class="risk.level">{{ levelLabel[risk.level] || risk.level }}</span></td>
            <td>{{ risk.clauseNo }}<br /><span class="muted">{{ risk.source === 'RULE' ? risk.ruleCode : sourceLabel[risk.source] }}</span></td>
            <td>
              {{ risk.content }}
              <div class="muted" v-if="risk.suggestion">建议：{{ risk.suggestion }}</div>
            </td>
            <td class="muted">
              {{ risk.sourceChunkKey || '—' }}<br />
              {{ risk.sourceExcerpt || '（无引用）' }}<br />
              {{ risk.citationVerified ? '引用已回验' : '引用未通过回验，待人工确认' }}
            </td>
          </tr>
          <tr v-if="rows.length === 0">
            <td colspan="4" class="muted">未发现规则命中或模型判定的风险项，仍须人工复核。</td>
          </tr>
        </tbody>
      </table>

      <div class="disclaimer">
        <div v-for="text in prohibitions" :key="text">{{ text }}</div>
        <strong>{{ disclaimer }}</strong>
      </div>
    </template>
  </section>
</template>
