<script setup>
import { onMounted, reactive, ref } from 'vue'
import { createRule, dryRunRule, listRules, refreshRules, setRuleEnabled, updateRule } from '../api'

const rules = ref([])
const includeDisabled = ref(true)
const error = ref('')
const info = ref('')
const busy = ref('')
const editing = ref(null)
const dryRunning = ref(null)

const emptyForm = () => ({
  ruleCode: '',
  ruleName: '',
  clauseType: 'PAYMENT',
  scope: 'CLAUSE',
  conditionExpr: '',
  riskLevel: 'MEDIUM',
  suggestionTemplate: '',
  enabled: false,
  positiveExample: '',
  negativeExample: '',
  ownerName: 'admin'
})
const form = reactive(emptyForm())
const dry = reactive({
  clauseText: '',
  clauseType: 'PAYMENT',
  contractAmount: '',
  paymentDays: '',
  jurisdiction: '',
  hasJointLiability: false,
  presentTypes: [],
  result: null
})

// 枚举 code 保持后端契约不变，界面只展示中文标签（用户不需要记英文码）
const clauseTypes = [
  { value: 'PAYMENT', label: '付款' },
  { value: 'BREACH', label: '违约' },
  { value: 'CONFIDENTIALITY', label: '保密' },
  { value: 'JURISDICTION', label: '争议管辖' },
  { value: 'DISCLAIMER', label: '免责' },
  { value: 'CONTRACT', label: '合同级' },
  { value: 'OTHER', label: '其他' }
]
const scopes = [
  { value: 'CLAUSE', label: '条款级' },
  { value: 'CONTRACT', label: '合同级' }
]
const levels = [
  { value: 'HIGH', label: '高' },
  { value: 'MEDIUM', label: '中' },
  { value: 'LOW', label: '低' }
]
const clauseTypeLabel = (code) => clauseTypes.find((item) => item.value === code)?.label || code
const scopeLabel = (code) => scopes.find((item) => item.value === code)?.label || code
const levelLabel = (code) => levels.find((item) => item.value === code)?.label || code

async function load() {
  error.value = ''
  try {
    rules.value = await listRules(includeDisabled.value)
  } catch (e) {
    error.value = e.message
  }
}

function openCreate() {
  Object.assign(form, emptyForm())
  editing.value = 'new'
  info.value = ''
}

function openEdit(rule) {
  Object.assign(form, {
    ruleCode: rule.ruleCode,
    ruleName: rule.ruleName,
    clauseType: rule.clauseType,
    scope: rule.scope,
    conditionExpr: rule.conditionExpr,
    riskLevel: rule.riskLevel,
    suggestionTemplate: rule.suggestionTemplate,
    enabled: rule.enabled,
    positiveExample: rule.positiveExample,
    negativeExample: rule.negativeExample,
    ownerName: rule.ownerName
  })
  editing.value = rule
}

async function save() {
  error.value = ''
  info.value = ''
  busy.value = 'save'
  try {
    if (editing.value === 'new') {
      const created = await createRule({ ...form })
      info.value = `已创建 ${created.ruleCode}（默认不启用，请先试算再启用）`
    } else {
      await updateRule(editing.value.id, { ...form })
      info.value = `已更新 ${form.ruleCode}`
    }
    editing.value = null
    await load()
  } catch (e) {
    error.value = `${e.message}（${e.code ?? '未知'}）`
  } finally {
    busy.value = ''
  }
}

async function toggle(rule) {
  busy.value = rule.id
  error.value = ''
  try {
    await setRuleEnabled(rule.id, !rule.enabled)
    info.value = `${rule.ruleCode} 已${rule.enabled ? '禁用' : '启用'}`
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

async function refreshCache() {
  busy.value = 'refresh'
  error.value = ''
  try {
    const result = await refreshRules()
    info.value = `缓存已刷新：租户规则 ${result.tenantRules} 条，启用 ${result.enabledRules} 条`
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

function openDry(rule) {
  dryRunning.value = rule
  dry.result = null
  dry.error = ''
}

async function runDry() {
  error.value = ''
  try {
    const payload = {
      clauseText: dry.clauseText,
      clauseType: dry.clauseType,
      contractAmount: dry.contractAmount === '' ? null : Number(dry.contractAmount),
      paymentDays: dry.paymentDays === '' ? null : Number(dry.paymentDays),
      jurisdiction: dry.jurisdiction || null,
      hasJointLiability: dry.hasJointLiability,
      presentTypes: dry.presentTypes.length ? dry.presentTypes : null
    }
    dry.result = await dryRunRule(dryRunning.value.id, payload)
  } catch (e) {
    error.value = `${e.message}（${e.code ?? '未知'}）`
  }
}

onMounted(load)
</script>

<template>
  <section class="panel">
    <div class="row" style="justify-content: space-between">
      <div>
        <h2 class="panel-title">规则管理</h2>
        <p class="muted" style="margin: 0">规则变更默认不启用，先试算再启用；启用后点“刷新缓存”立即生效。</p>
      </div>
      <div class="row">
        <label class="field checkbox">
          <input v-model="includeDisabled" type="checkbox" @change="load" />
          <span>显示未启用</span>
        </label>
        <button class="secondary" :disabled="busy === 'refresh'" @click="refreshCache">刷新缓存</button>
        <button @click="openCreate">新建规则</button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="info" class="muted">{{ info }}</p>

    <table>
      <thead>
        <tr>
          <th style="width: 210px">编码</th>
          <th>名称</th>
          <th style="width: 120px">类型/作用域</th>
          <th style="width: 70px">等级</th>
          <th style="width: 70px">版本</th>
          <th style="width: 70px">状态</th>
          <th style="width: 220px">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="rule in rules" :key="rule.id">
          <td><code>{{ rule.ruleCode }}</code></td>
          <td>{{ rule.ruleName }}</td>
          <td class="muted">{{ clauseTypeLabel(rule.clauseType) }} / {{ scopeLabel(rule.scope) }}</td>
          <td><span class="badge" :class="rule.riskLevel">{{ levelLabel(rule.riskLevel) }}</span></td>
          <td>v{{ rule.versionNo }}</td>
          <td>
            <span :class="rule.enabled ? 'tag-on' : 'tag-off'">{{ rule.enabled ? '启用' : '停用' }}</span>
          </td>
          <td>
            <button class="secondary" :disabled="busy === rule.id" @click="openDry(rule)">试算</button>
            <button class="secondary" :disabled="busy === rule.id" @click="openEdit(rule)">编辑</button>
            <button class="secondary" :disabled="busy === rule.id" @click="toggle(rule)">
              {{ rule.enabled ? '禁用' : '启用' }}
            </button>
          </td>
        </tr>
        <tr v-if="rules.length === 0">
          <td colspan="7" class="muted">暂无规则。</td>
        </tr>
      </tbody>
    </table>

    <div v-if="editing" class="modal-mask">
      <div class="modal">
        <div class="modal-head">
          <h3 class="panel-title">{{ editing === 'new' ? '新建规则' : '编辑规则：' + form.ruleCode }}</h3>
          <button class="modal-close" @click="editing = null">关闭</button>
        </div>
      <div class="form-grid">
        <label class="field">规则编码<input v-model="form.ruleCode" :disabled="editing !== 'new'" placeholder="PAYMENT.MAX_DAYS.01" /></label>
        <label class="field">规则名称<input v-model="form.ruleName" /></label>
        <label class="field">条款类型
          <select v-model="form.clauseType">
            <option v-for="type in clauseTypes" :key="type.value" :value="type.value">{{ type.label }}</option>
          </select>
        </label>
        <label class="field">作用域
          <select v-model="form.scope">
            <option v-for="scope in scopes" :key="scope.value" :value="scope.value">{{ scope.label }}</option>
          </select>
        </label>
        <label class="field">风险等级
          <select v-model="form.riskLevel">
            <option v-for="level in levels" :key="level.value" :value="level.value">{{ level.label }}</option>
          </select>
        </label>
        <label class="field">维护人<input v-model="form.ownerName" /></label>
        <label class="field" style="grid-column: 1 / -1">条件表达式<input v-model="form.conditionExpr" placeholder="paymentDays > 90" /></label>
        <label class="field" style="grid-column: 1 / -1">建议模板<input v-model="form.suggestionTemplate" /></label>
        <label class="field" style="grid-column: 1 / -1">正例 JSON<textarea v-model="form.positiveExample" rows="3" placeholder='{"expected":true,"clauseText":"120 日内付款","paymentDays":120}'></textarea></label>
        <label class="field" style="grid-column: 1 / -1">反例 JSON<textarea v-model="form.negativeExample" rows="3" placeholder='{"expected":false,"clauseText":"30 日内付款","paymentDays":30}'></textarea></label>
      </div>
      <div class="row">
        <button :disabled="busy === 'save'" @click="save">保存</button>
        <button class="secondary" @click="editing = null">取消</button>
      </div>
      </div>
    </div>

    <div v-if="dryRunning" class="modal-mask">
      <div class="modal">
        <div class="modal-head">
          <h3 class="panel-title">试算：{{ dryRunning.ruleCode }}</h3>
          <button class="modal-close" @click="dryRunning = null">关闭</button>
        </div>
      <div class="form-grid">
        <label class="field">条款文本<input v-model="dry.clauseText" /></label>
        <label class="field">条款类型
          <select v-model="dry.clauseType">
            <option v-for="type in clauseTypes" :key="type.value" :value="type.value">{{ type.label }}</option>
          </select>
        </label>
        <label class="field">合同金额<input v-model="dry.contractAmount" type="number" /></label>
        <label class="field">账期（天）<input v-model="dry.paymentDays" type="number" /></label>
        <label class="field">管辖地<input v-model="dry.jurisdiction" /></label>
        <label class="field" style="grid-column: 1 / -1">涉及条款类型（可多选）
          <div class="row" style="gap: 14px; flex-wrap: wrap">
            <label v-for="type in clauseTypes" :key="type.value" class="field checkbox">
              <input v-model="dry.presentTypes" type="checkbox" :value="type.value" />
              <span>{{ type.label }}</span>
            </label>
          </div>
        </label>
        <label class="field checkbox"><input v-model="dry.hasJointLiability" type="checkbox" />含连带责任</label>
      </div>
      <div class="row">
        <button @click="runDry">执行试算</button>
        <button class="secondary" @click="dryRunning = null">关闭</button>
      </div>
      <p v-if="dry.result" class="muted">
        命中：<strong>{{ dry.result.matched ? '是' : '否' }}</strong>
        <span v-if="dry.result.riskLevel"> · 等级：{{ dry.result.riskLevel }}</span>
      </p>
      </div>
    </div>
  </section>
</template>
