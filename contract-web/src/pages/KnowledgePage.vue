<script setup>
import { onMounted, reactive, ref } from 'vue'
import { createKbDoc, listKbDocs, publishKb, syncKb, unpublishKb, updateKbContent, uploadKbDocument } from '../api'

const docs = ref([])
const error = ref('')
const info = ref('')
const busy = ref('')
const kbFile = ref(null)
const form = reactive({
  title: '',
  docType: 'INSTITUTION',
  content: '',
  activate: true
})
const editing = reactive({ docId: '', content: '' })

const docTypeText = {
  INSTITUTION: '制度',
  CASE: '判例'
}

const docTypes = [
  { value: 'INSTITUTION', label: '制度' },
  { value: 'CASE', label: '判例' }
]

async function load() {
  try {
    docs.value = await listKbDocs()
  } catch (e) {
    error.value = e.message
  }
}

function onKbFile(event) {
  kbFile.value = event.target.files?.[0] || null
}

async function importFile() {
  error.value = ''
  info.value = ''
  if (!kbFile.value) {
    error.value = '请选择 PDF / DOC / DOCX 文件'
    return
  }
  busy.value = 'upload'
  try {
    const result = await uploadKbDocument(kbFile.value, {
      title: form.title.trim() || undefined,
      docType: form.docType,
      activate: form.activate
    })
    info.value = `已从文件导入：${result.title}（${result.syncStatus}）`
    kbFile.value = null
    await load()
  } catch (e) {
    error.value = `${e.message}（${e.code ?? '未知'}）`
  } finally {
    busy.value = ''
  }
}

async function create() {  error.value = ''
  info.value = ''
  if (!form.title.trim() || !form.content.trim()) {
    error.value = '标题与正文不能为空'
    return
  }
  busy.value = 'create'
  try {
    const created = await createKbDoc({ ...form })
    info.value = `已创建：${created.title}（${created.docId}），状态 ${created.syncStatus}`
    form.title = ''
    form.content = ''
    await load()
  } catch (e) {
    error.value = `${e.message}（${e.code ?? '未知'}）`
  } finally {
    busy.value = ''
  }
}

async function toggleActive(doc) {
  busy.value = doc.docId
  error.value = ''
  try {
    if (doc.active) {
      await unpublishKb(doc.docId)
      info.value = `已下架：${doc.title}`
    } else {
      await publishKb(doc.docId)
      info.value = `已上架：${doc.title}`
    }
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

async function resync(doc) {
  busy.value = doc.docId
  error.value = ''
  try {
    const outcome = await syncKb(doc.docId)
    info.value = `同步完成：新增 ${outcome.inserted}，更新 ${outcome.updated}，跳过 ${outcome.skipped}，删除 ${outcome.deleted}`
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

function startEdit(doc) {
  editing.docId = doc.docId
  editing.content = ''
}

async function saveContent() {
  if (!editing.content.trim()) {
    error.value = '正文不能为空'
    return
  }
  busy.value = editing.docId
  error.value = ''
  try {
    await updateKbContent(editing.docId, editing.content)
    info.value = '正文已更新，已触发增量重嵌入'
    editing.docId = ''
    editing.content = ''
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

onMounted(load)
</script>

<template>
  <section class="panel">
    <h2 class="panel-title">知识库管理</h2>
    <p class="muted">仅上架版本参与检索；正文更新只重嵌入变化切片，未变化切片不重写。</p>

    <div class="kb-form">
      <label class="field">
        标题
        <input v-model="form.title" placeholder="例如：采购管理制度" />
      </label>
      <label class="field">
        类型
        <select v-model="form.docType">
          <option v-for="type in docTypes" :key="type.value" :value="type.value">{{ type.label }}</option>
        </select>
      </label>
      <label class="checkbox-hint">
        <input type="checkbox" v-model="form.activate" />
        <span>
          创建后立即上架
          <small>勾选后马上触发向量写入并参与检索；不勾选则保持草稿，稍后可手动上架。</small>
        </span>
      </label>
      <div class="row" style="grid-column: 1 / -1; border-top: 1px dashed var(--border); padding-top: 12px">
        <label class="field" style="flex: 1">
          从 PDF / DOC / DOCX 导入（≤50MB）
          <input type="file" accept=".pdf,.doc,.docx" @change="onKbFile" />
        </label>
        <button class="secondary" :disabled="busy === 'upload'" @click="importFile">
          {{ busy === 'upload' ? '解析中…' : '导入并创建' }}
        </button>
      </div>
      <p class="muted" style="grid-column: 1 / -1; margin: 0">
        文件方式会复用合同解析链路（格式 + 魔数校验、Tika 解析），正文只写回知识库，不生成合同记录；
        上面填了标题就用标题，否则取文件名。
      </p>
      <label class="field" style="grid-column: 1 / -1">
        正文（也可以直接粘贴）
        <textarea v-model="form.content" rows="5" placeholder="粘贴制度/判例正文，条款以“第X条”分隔"></textarea>
      </label>
      <div class="row">
        <button :disabled="busy === 'create'" @click="create">{{ busy === 'create' ? '创建中…' : '新建文档' }}</button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="info" class="muted">{{ info }}</p>

    <table>
      <thead>
        <tr>
          <th>标题</th>
          <th style="width: 110px">类型</th>
          <th style="width: 70px">版本</th>
          <th style="width: 80px">上架</th>
          <th style="width: 100px">同步状态</th>
          <th style="width: 120px">更新时间</th>
          <th style="width: 260px">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="doc in docs" :key="doc.docId">
          <td>{{ doc.title }}</td>
          <td class="muted">{{ docTypeText[doc.docType] || doc.docType }}</td>
          <td>v{{ doc.versionNo }}</td>
          <td>
            <span :class="doc.active ? 'tag-on' : 'tag-off'">{{ doc.active ? '上架' : '下架' }}</span>
          </td>
          <td class="muted">{{ doc.syncStatus }}</td>
          <td class="muted">{{ (doc.updatedAt || '').replace('T', ' ').slice(0, 19) }}</td>
          <td>
            <button class="secondary" :disabled="busy === doc.docId" @click="toggleActive(doc)">
              {{ doc.active ? '下架' : '上架' }}
            </button>
            <button class="secondary" :disabled="busy === doc.docId" @click="resync(doc)">同步</button>
            <button class="secondary" :disabled="busy === doc.docId" @click="startEdit(doc)">更新正文</button>
          </td>
        </tr>
        <tr v-if="docs.length === 0">
          <td colspan="7" class="muted">暂无文档，先在上方新建一份。</td>
        </tr>
      </tbody>
    </table>

    <div v-if="editing.docId" class="modal-mask">
      <div class="modal">
        <div class="modal-head">
          <h3 class="panel-title">更新正文（{{ editing.docId }}）</h3>
          <button class="modal-close" @click="editing.docId = ''">关闭</button>
        </div>
        <label class="field">
          新的完整正文
          <textarea v-model="editing.content" rows="10" placeholder="粘贴新的完整正文；保存后仅变化切片重嵌入"></textarea>
        </label>
        <div class="row" style="margin-top: 12px">
          <button :disabled="busy === editing.docId" @click="saveContent">保存</button>
          <button class="secondary" @click="editing.docId = ''">取消</button>
        </div>
      </div>
    </div>
  </section>
</template>
