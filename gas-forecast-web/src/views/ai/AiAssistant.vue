<template>
  <div class="standalone-shell">
  <section class="assistant-page" @click="hideSessionMenu">
    <aside class="session-panel">
      <div class="brand">
        <div class="brand-icon"><el-icon><MagicStick /></el-icon></div>
        <div><strong>AI 智能问答</strong><span>天然气预测决策助手</span></div>
      </div>
      <el-button class="new-session" @click="createSession"><el-icon><Plus /></el-icon>新建会话</el-button>
      <div class="session-caption">最近会话</div>
      <div
        v-for="item in sessions"
        :key="item.id"
        class="session-row"
        :class="{ active: item.id === activeSessionId }"
        @contextmenu.prevent.stop="openSessionMenu($event, item)"
      >
        <button class="session-item" @click="switchSession(item.id)">
          <span>{{ item.title }}</span><small>{{ item.updatedAt }}</small>
        </button>
      </div>
    </aside>

    <div
      v-if="sessionMenu.visible"
      class="session-context-menu"
      :style="{ left: `${sessionMenu.x}px`, top: `${sessionMenu.y}px` }"
      @click.stop
    >
      <button type="button" @click="renameMenuSession">
        <el-icon><Edit /></el-icon><span>重命名</span>
      </button>
      <button type="button" class="danger" @click="deleteMenuSession">
        <el-icon><DeleteIcon /></el-icon><span>删除</span>
      </button>
    </div>

    <main class="chat-panel">
      <header class="chat-header">
        <div><h1>智能预测助手</h1><p>查询预测结果、解释趋势，并支持连续追问</p></div>
        <div class="online"><i></i>服务正常</div>
      </header>

      <div ref="messageContainer" class="messages">
        <div v-if="messages.length === 1" class="welcome">
          <div class="welcome-icon"><el-icon><MagicStick /></el-icon></div>
          <h2>您好，有什么可以帮您？</h2>
          <p>选择一个预测批次后，我会结合真实预测结果、历史值和模型分析回答。</p>
          <div class="quick-list">
            <button v-for="text in quickQuestions" :key="text" @click="ask(text)">{{ text }}</button>
          </div>
        </div>

        <article v-for="message in messages" v-show="message.role === 'user' || Boolean(message.content)" :key="message.id" class="message" :class="message.role">
          <div class="avatar">{{ message.role === 'user' ? '我' : 'AI' }}</div>
          <div class="message-main">
            <div class="message-name">{{ message.role === 'user' ? '您' : 'AI 预测助手' }}</div>
            <div v-if="message.role === 'user'" class="user-text">{{ message.content }}</div>
            <div v-else class="assistant-text" v-html="sanitizeHtml(message.content)"></div>
            <details v-if="validProgress(message.progress)" class="execution-trace" open>
              <summary>
                <i></i><span>{{ currentStepName(message.progress!) }}</span>
                <small>{{ message.progress!.currentStep }}/{{ message.progress!.totalSteps }}</small>
              </summary>
              <div v-for="step in message.progress!.steps" :key="step.index" class="trace-step" :class="step.status.toLowerCase()">
                <b>{{ step.status === 'SUCCESS' ? '✓' : step.index }}</b>
                <span>{{ step.name }}<small v-if="step.detail">{{ step.detail }}</small></span>
                <em>{{ stepStatusText(step.status) }}</em>
              </div>
            </details>
            <AiForecastResult v-if="message.forecastResult" :result="message.forecastResult" />
            <section v-if="message.analysis" class="ai-analysis">
              <div class="analysis-title"><el-icon><MagicStick /></el-icon><strong>AI 智能分析</strong></div>
              <div v-html="sanitizeHtml(renderMarkdown(message.analysis))"></div>
            </section>
            <div v-if="message.suggestedQuestions?.length" class="follow-up">
              <span>您还可以继续问</span>
              <button v-for="question in message.suggestedQuestions" :key="question" type="button" @click="ask(question, true)">{{ question }}</button>
            </div>
          </div>
        </article>
        <div v-if="streaming" class="message assistant">
          <div class="avatar">AI</div><div class="typing"><i></i><i></i><i></i></div>
        </div>
      </div>

      <footer class="composer">
        <el-input
          v-model="input"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 5 }"
          placeholder="例如：解释这个批次未来30天的趋势和风险"
          resize="none"
          @keydown.enter.exact.prevent="send()"
        />
        <el-button v-if="streaming" circle type="danger" @click="stop"><el-icon><VideoPause /></el-icon></el-button>
        <el-button v-else circle type="primary" :disabled="!input.trim()" @click="send()"><el-icon><Promotion /></el-icon></el-button>
        <p>AI 内容用于辅助决策，最终结果请以业务数据和模型输出为准。</p>
      </footer>
    </main>

  </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { Delete as DeleteIcon, Edit, MagicStick, Plus, Promotion, VideoPause } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getJson, postJson } from '@/api/management'
import { renderMarkdown, sanitizeHtml } from '@/utils/markdown'
import AiForecastResult from './AiForecastResult.vue'

type Role = 'user' | 'assistant'
interface ProgressStep { index: number; name: string; status: 'SUCCESS' | 'RUNNING' | 'WAITING' | 'FAILED'; detail?: string }
interface ExecutionProgress { title: string; currentStep: number; totalSteps: number; steps: ProgressStep[] }
interface ForecastPoint { date: string; value: number }
interface ForecastResult { batchNo: string; average: number; minimum: number; maximum: number; points: ForecastPoint[]; training?: { batchNo: string } }
interface Message { id: string; role: Role; content: string; progress?: ExecutionProgress; forecastResult?: ForecastResult; analysis?: string; suggestedQuestions?: string[] }
interface Session { id: string; conversationNo?: string; title: string; updatedAt: string; messages: Message[] }

const welcomeMessage = (): Message => ({
  id: crypto.randomUUID(), role: 'assistant',
  content: renderMarkdown('您好！我是燃气预测智能体。您可以直接告诉我客户、地区和预测需求，我会识别业务实体并执行训练、预测或任务查询。')
})
const quickQuestions = ['预测南京港华未来30天销量', '训练南京中燃的城燃模型', '刚才的训练执行到哪了？', '查询刚才的预测结果']
const sessions = ref<Session[]>([])
const activeSessionId = ref('')
const input = ref('')
const streaming = ref(false)
const abortController = ref<AbortController | null>(null)
const messageContainer = ref<HTMLElement>()
const sessionMenu = ref({ visible: false, x: 0, y: 0, sessionId: '' })

const activeSession = computed(() => sessions.value.find((item) => item.id === activeSessionId.value))
const messages = computed(() => activeSession.value?.messages || [])

const nowText = () => new Date().toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
const createSession = () => {
  const session: Session = { id: crypto.randomUUID(), title: '新会话', updatedAt: nowText(), messages: [welcomeMessage()] }
  sessions.value.unshift(session); activeSessionId.value = session.id
}
const renameSession = async (session: Session) => {
  if (!session.conversationNo) return
  try {
    const { value } = await ElMessageBox.prompt('请输入新的会话名称', '修改会话名称', {
      inputValue: session.title,
      inputPattern: /\S+/,
      inputErrorMessage: '会话名称不能为空',
      inputValidator: (text) => text.length <= 128 || '最多输入128个字符'
    })
    const title = value.trim()
    await postJson('/ai/conversations/rename', { conversationNo: session.conversationNo, title })
    session.title = title
    ElMessage.success('会话名称已修改')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '修改失败')
  }
}
const deleteSession = async (session: Session) => {
  if (streaming.value) return
  try {
    await ElMessageBox.confirm(`确定删除会话“${session.title}”吗？删除后不可恢复。`, '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
    if (session.conversationNo) {
      await getJson(`/ai/conversations/delete?conversationNo=${encodeURIComponent(session.conversationNo)}`)
    }
    const deletedActive = session.id === activeSessionId.value
    sessions.value = sessions.value.filter((item) => item.id !== session.id)
    if (!sessions.value.length) {
      createSession()
    } else if (deletedActive) {
      activeSessionId.value = sessions.value[0].id
      await switchSession(sessions.value[0].id)
    }
    ElMessage.success('会话已删除')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '删除失败')
  }
}
const openSessionMenu = (event: MouseEvent, session: Session) => {
  sessionMenu.value = { visible: true, x: event.clientX, y: event.clientY, sessionId: session.id }
}
const hideSessionMenu = () => {
  sessionMenu.value.visible = false
}
const menuSession = () => sessions.value.find((item) => item.id === sessionMenu.value.sessionId)
const renameMenuSession = async () => {
  const session = menuSession()
  hideSessionMenu()
  if (session) await renameSession(session)
}
const deleteMenuSession = async () => {
  const session = menuSession()
  hideSessionMenu()
  if (session) await deleteSession(session)
}
const switchSession = async (id: string) => {
  if (streaming.value) return
  activeSessionId.value = id
  const session = activeSession.value
  if (!session?.conversationNo) return
  try {
    const response = await getJson<any>(`/ai/conversations/detail?conversationNo=${encodeURIComponent(session.conversationNo)}`)
    const detail = response.data
    if (detail?.messages) {
      session.messages = detail.messages.map((item: any) => ({
        id: String(item.id), role: item.role, content: item.role === 'assistant' ? renderMarkdown(item.content) : item.content,
        progress: item.progress || undefined,
        forecastResult: item.forecastResult || undefined,
        analysis: item.analysis || undefined,
        suggestedQuestions: item.suggestedQuestions || undefined
      }))
      await reconcileTrainingMessages(session.messages)
      scrollBottom()
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '历史会话内容加载失败，请稍后重试')
  }
}
const scrollBottom = () => nextTick(() => { if (messageContainer.value) messageContainer.value.scrollTop = messageContainer.value.scrollHeight })
const stepStatusText = (status: ProgressStep['status']) => ({ SUCCESS: '已完成', RUNNING: '执行中', WAITING: '等待中', FAILED: '失败' }[status])
const validProgress = (progress?: ExecutionProgress) => Boolean(progress && progress.totalSteps > 0 && progress.currentStep > 0 && Array.isArray(progress.steps))
const currentStepName = (progress: ExecutionProgress) => progress.steps.find((step) => step.index === progress.currentStep)?.name || progress.title

const ask = (text: string, contextualFollowUp = false) => { input.value = text; void send(contextualFollowUp) }
const stop = () => abortController.value?.abort()
const send = async (contextualFollowUp = false) => {
  // Vue 事件绑定遗漏括号时可能传入 MouseEvent/KeyboardEvent，接口只接受明确布尔值。
  const isContextualFollowUp = contextualFollowUp === true
  const text = input.value.trim()
  const session = activeSession.value
  if (!text || !session || streaming.value) return
  input.value = ''
  session.messages.push({ id: crypto.randomUUID(), role: 'user', content: text })
  if (session.title === '新会话') session.title = text.slice(0, 18)
  session.updatedAt = nowText()
  const answer: Message = { id: crypto.randomUUID(), role: 'assistant', content: '' }
  session.messages.push(answer)
  streaming.value = true; scrollBottom()
  const controller = new AbortController(); abortController.value = controller
  try {
    const response = await postJson<any>('/ai/conversations/chat', {
      conversationNo: session.conversationNo || null,
      message: text,
      contextualFollowUp: isContextualFollowUp
    }, controller.signal)
    const data = response.data || {}
    if (data.conversationNo) {
      session.conversationNo = data.conversationNo
      session.id = data.conversationNo
      activeSessionId.value = data.conversationNo
    }
    answer.content = renderMarkdown(data.answer || 'AI 未返回有效内容')
    answer.progress = data.progress || undefined
    answer.forecastResult = data.forecastResult || undefined
    answer.analysis = data.analysis || undefined
    answer.suggestedQuestions = data.suggestedQuestions || undefined
    if (data.trainBatchNo && data.status === 'WAITING_TRAINING') {
      void watchTraining(data.trainBatchNo, answer)
    }
    if (data.forecastBatchNo && data.status === 'EXECUTING') {
      void watchForecast(data.forecastBatchNo, answer)
    }
    if (data.status === 'NEED_CONFIRMATION' && Array.isArray(data.candidates) && data.candidates.length) {
      answer.content += renderMarkdown('\n\n请回复序号或完整名称。')
    }
    await loadServerSessions(session)
  } catch (error: any) {
    answer.content = renderMarkdown(error?.name === 'AbortError' ? '已停止处理。' : `⚠️ ${error?.message || '问答失败'}`)
  } finally {
    streaming.value = false; abortController.value = null; scrollBottom()
  }
}

const watchTraining = async (batchNo: string, message: Message) => {
  for (let count = 0; count < 120; count += 1) {
    await new Promise((resolve) => window.setTimeout(resolve, 3000))
    try {
      const response = await getJson<any>(`/ai/conversations/training-progress?batchNo=${encodeURIComponent(batchNo)}`)
      const batch = response.data
      if (!batch) continue
      if (applyTrainingStatus(message, batchNo, batch, true)) {
        if (String(batch.status) === 'SUCCESS') await continueForecastAfterTraining(batchNo, message)
        return
      }
    } catch {
      // 临时查询失败时继续下一轮，不中断当前对话。
    }
  }
  const trainStep = message.progress?.steps.find((item) => item.index === 2)
  if (trainStep?.status === 'RUNNING') {
    trainStep.status = 'FAILED'
    trainStep.detail = '页面等待超时，请刷新或查询训练状态'
    message.content += renderMarkdown('\n\n⚠️ 页面等待训练结果超时，已停止自动轮询。任务可能仍在后台执行，可刷新会话或询问“训练到哪了”。')
    scrollBottom()
  }
}

const continuingBatches = new Set<string>()
const continueForecastAfterTraining = async (batchNo: string, sourceMessage?: Message) => {
  const session = activeSession.value
  if (!session?.conversationNo || continuingBatches.has(batchNo)) return
  if (session.messages.some((item) => item.forecastResult?.training?.batchNo === batchNo)) return
  const attemptKey = `ai-auto-forecast:${session.conversationNo}:${batchNo}`
  const lastAttemptAt = Number(window.sessionStorage.getItem(attemptKey) || 0)
  // 页面恢复、路由重挂载可能同时触发多次核对，短时间内只允许一次自动续跑。
  if (Date.now() - lastAttemptAt < 30_000) return
  window.sessionStorage.setItem(attemptKey, String(Date.now()))
  continuingBatches.add(batchNo)
  try {
    const response = await postJson<any>('/ai/conversations/continue-forecast', {
      conversationNo: session.conversationNo,
      trainBatchNo: batchNo
    })
    const data = response.data || {}
    if (sourceMessage) {
      const answer = data.answer || '预测已完成'
      if (!sourceMessage.content.includes(answer)) sourceMessage.content += renderMarkdown(`\n\n${answer}`)
      sourceMessage.progress = data.progress || sourceMessage.progress
      sourceMessage.forecastResult = data.forecastResult || undefined
      sourceMessage.analysis = data.analysis || undefined
      sourceMessage.suggestedQuestions = data.suggestedQuestions || undefined
    } else {
      session.messages.push({
        id: crypto.randomUUID(), role: 'assistant', content: renderMarkdown(data.answer || '预测已完成'),
        progress: data.progress || undefined, forecastResult: data.forecastResult || undefined,
        analysis: data.analysis || undefined, suggestedQuestions: data.suggestedQuestions || undefined
      })
    }
    if (data.forecastBatchNo && data.status === 'EXECUTING') {
      void watchForecast(data.forecastBatchNo, sourceMessage || session.messages[session.messages.length - 1])
    }
    window.sessionStorage.removeItem(attemptKey)
    await loadServerSessions(session)
    scrollBottom()
  } catch (error: any) {
    const failureText = `训练已完成，但自动预测失败：${error?.message || '请稍后重试'}`
    // 失败信息归并到原执行流程，不再为一次失败反复创建多个助手气泡。
    if (sourceMessage && !sourceMessage.content.includes(failureText)) {
      sourceMessage.content += renderMarkdown(`\n\n⚠️ ${failureText}`)
      const predictStep = sourceMessage.progress?.steps.find((item) => item.index === 3)
      if (predictStep) { predictStep.status = 'FAILED'; predictStep.detail = error?.message || '自动预测请求失败' }
    }
    scrollBottom()
  } finally {
    continuingBatches.delete(batchNo)
  }
}

const watchingForecasts = new Set<string>()
const watchForecast = async (batchNo: string, message: Message) => {
  const session = activeSession.value
  if (!session?.conversationNo || watchingForecasts.has(batchNo)) return
  watchingForecasts.add(batchNo)
  try {
    for (let count = 0; count < 240; count += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 2000))
      try {
        const response = await getJson<any>(`/ai/conversations/forecast-progress?conversationNo=${encodeURIComponent(session.conversationNo!)}&batchNo=${encodeURIComponent(batchNo)}`)
        const data = response.data || {}
        if (data.progress) message.progress = data.progress
        if (data.status === 'EXECUTING') { scrollBottom(); continue }
        const answer = data.answer || (data.status === 'FAILED' ? '预测执行失败' : '预测已完成')
        if (!message.content.includes(answer)) message.content += renderMarkdown(`\n\n${data.status === 'FAILED' ? '⚠️ ' : ''}${answer}`)
        message.forecastResult = data.forecastResult || undefined
        message.analysis = data.analysis || undefined
        message.suggestedQuestions = data.suggestedQuestions || undefined
        await loadServerSessions(session)
        scrollBottom()
        return
      } catch (error: any) {
        // 短暂网络异常不改变后台任务状态，下一轮继续核对。
        if (count < 239) continue
        const step = message.progress?.steps.find((item) => item.index === 3)
        if (step) { step.status = 'FAILED'; step.detail = error?.message || '无法获取预测状态' }
      }
    }
    const step = message.progress?.steps.find((item) => item.index === 3)
    if (step?.status === 'RUNNING') {
      step.status = 'FAILED'
      step.detail = '页面等待超时，预测可能仍在后台执行，请稍后刷新会话'
    }
  } finally {
    watchingForecasts.delete(batchNo)
    scrollBottom()
  }
}

const applyTrainingStatus = (message: Message, batchNo: string, batch: any, appendNotice: boolean) => {
  const progress = message.progress
  const status = String(batch.status || '')
  const trainStep = progress?.steps.find((item) => item.index === 2)
  const failed = ['FAILED', 'FAILED_FINAL', 'PREPARE_FAILED'].includes(status)
  if (trainStep) {
    trainStep.status = status === 'SUCCESS' ? 'SUCCESS' : failed ? 'FAILED' : 'RUNNING'
    trainStep.detail = failed ? (batch.message || '训练未能完成') : batchNo
  }
  if (failed) {
    if (progress) progress.currentStep = 2
    if (appendNotice && !message.content.includes('本次训练未能完成'))
      message.content += renderMarkdown(`\n\n⚠️ ${batch.message || '本次训练未能完成，请稍后重试或联系平台管理员。'}`)
    scrollBottom()
    return true
  }
  if (status === 'SUCCESS') {
    if (progress?.totalSteps === 3) {
      progress.currentStep = 3
      const saveStep = progress.steps.find((item) => item.index === 3)
      if (saveStep) { saveStep.status = 'SUCCESS'; saveStep.detail = '已完成' }
    } else if (progress) {
      // 预测流程尚未真正发起下一步时，训练已完成，但预测仍保持等待状态。
      progress.currentStep = 2
      const predictStep = progress.steps.find((item) => item.index === 3)
      if (predictStep) predictStep.status = 'WAITING'
    }
    if (appendNotice && !message.content.includes('训练已经完成'))
      message.content += renderMarkdown('\n\n训练已经完成，可以继续执行预测。')
    scrollBottom()
    return true
  }
  return false
}

const reconcileTrainingMessages = async (items: Message[]) => {
  for (const message of items) {
    const progress = message.progress
    const predictStep = Array.isArray(progress?.steps) ? progress.steps.find((item) => item.index === 3) : undefined
    if (predictStep?.status === 'RUNNING' && predictStep.detail?.startsWith('FORECAST-')) {
      void watchForecast(predictStep.detail, message)
      continue
    }
    const trainStep = Array.isArray(progress?.steps) ? progress.steps.find((item) => item.index === 2) : undefined
    const batchNo = trainStep?.detail
    if (!progress || !batchNo || trainStep.status !== 'RUNNING') continue
    try {
      const response = await getJson<any>(`/ai/conversations/training-progress?batchNo=${encodeURIComponent(batchNo)}`)
      const terminal = response.data ? applyTrainingStatus(message, batchNo, response.data, false) : false
      if (!terminal) void watchTraining(batchNo, message)
      else if (String(response.data?.status) === 'SUCCESS') void continueForecastAfterTraining(batchNo, message)
    } catch {
      // 会话仍可正常展示，后续轮询会继续核对状态。
      void watchTraining(batchNo, message)
    }
  }
}

const loadServerSessions = async (current?: Session) => {
  const response = await getJson<any[]>('/ai/conversations')
  const remote = response.data || []
  const mapped: Session[] = remote.map((item: any) => ({
    id: item.conversationNo, conversationNo: item.conversationNo, title: item.title,
    updatedAt: new Date(item.updatedAt).toLocaleString('zh-CN'), messages: []
  }))
  if (current?.conversationNo) {
    const target = mapped.find((item) => item.conversationNo === current.conversationNo)
    if (target) target.messages = current.messages
  }
  sessions.value = mapped
  if (current?.conversationNo) activeSessionId.value = current.conversationNo
  if (!sessions.value.length) createSession()
}

let bulkProgressTimer: number | undefined
const refreshActiveBulkProgress = async () => {
  const session = activeSession.value
  if (!session?.conversationNo || streaming.value) return
  const hasRunningBulk = session.messages.some((message) =>
    message.progress?.title === '批量智能预测' && message.progress.steps.some((step) => step.status === 'RUNNING')
  )
  if (!hasRunningBulk) return
  try {
    const response = await getJson<any>(`/ai/conversations/detail?conversationNo=${encodeURIComponent(session.conversationNo)}`)
    if (!response.data?.messages) return
    session.messages = response.data.messages.map((item: any) => ({
      id: String(item.id), role: item.role, content: item.role === 'assistant' ? renderMarkdown(item.content) : item.content,
      progress: item.progress || undefined,
      forecastResult: item.forecastResult || undefined,
      analysis: item.analysis || undefined,
      suggestedQuestions: item.suggestedQuestions || undefined
    }))
  } catch {
    // 临时查询失败时保留现有进度，下一轮继续刷新。
  }
}

onMounted(async () => {
  try {
    await loadServerSessions()
  } catch (error: any) {
    sessions.value = []
    createSession()
    ElMessage.error(error?.message || '历史会话列表加载失败，请稍后重试')
    return
  }
  if (sessions.value.length && sessions.value[0].conversationNo) {
    activeSessionId.value = sessions.value[0].id
    await switchSession(sessions.value[0].id)
  }
  bulkProgressTimer = window.setInterval(() => void refreshActiveBulkProgress(), 3000)
})
onBeforeUnmount(() => {
  if (bulkProgressTimer) window.clearInterval(bulkProgressTimer)
})
</script>

<style scoped>
.standalone-shell{width:100%;height:100%;min-height:0;overflow:hidden;background:#eef3f9}.assistant-page{width:100%;height:100%;min-height:0;display:grid;grid-template-columns:300px minmax(480px,1fr);background:#fff;overflow:hidden}
.session-panel{min-height:0;background:#f8fafc;padding:20px;border-right:1px solid #e8edf5;overflow:auto}
.brand{display:flex;gap:11px;align-items:center;margin-bottom:20px}.brand-icon,.welcome-icon{display:grid;place-items:center;color:#fff;background:linear-gradient(135deg,#4776ef,#7c5cff);border-radius:10px;width:38px;height:38px}.brand strong,.brand span{display:block}.brand span{font-size:12px;color:#94a3b8;margin-top:3px}.new-session{width:100%;height:38px}.session-caption{font-size:12px;color:#94a3b8;margin:22px 4px 8px}.session-row{display:flex;align-items:center;border-radius:8px}.session-row:hover,.session-row.active{background:#eaf0ff;color:#315ecc}.session-item{min-width:0;flex:1;border:0;background:transparent;text-align:left;padding:10px;color:inherit;cursor:pointer}.session-item span,.session-item small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.session-item small{color:#94a3b8;margin-top:4px}
.session-context-menu{position:fixed;z-index:3000;min-width:132px;padding:6px;border:1px solid #dbe4f0;border-radius:8px;background:#fff;box-shadow:0 12px 32px rgba(15,23,42,.16)}.session-context-menu button{width:100%;height:34px;display:flex;align-items:center;gap:8px;border:0;border-radius:6px;background:transparent;color:#334155;text-align:left;cursor:pointer}.session-context-menu button:hover{background:#f1f5ff;color:#315ecc}.session-context-menu button.danger{color:#dc2626}.session-context-menu button.danger:hover{background:#fef2f2;color:#b91c1c}
.chat-panel{min-width:0;min-height:0;overflow:hidden;display:flex;flex-direction:column}.chat-header{height:74px;flex:0 0 74px;padding:0 24px;border-bottom:1px solid #edf0f5;display:flex;align-items:center;justify-content:space-between}.chat-header h1{font-size:18px;margin:0}.chat-header p{font-size:12px;color:#94a3b8;margin:5px 0 0}.online{font-size:12px;color:#16a34a}.online i{display:inline-block;width:7px;height:7px;background:#22c55e;border-radius:50%;margin-right:6px}
.messages{flex:1 1 auto;min-height:0;overflow-y:auto;overflow-x:hidden;overscroll-behavior:contain;padding:28px 8%;scroll-behavior:smooth}.welcome{text-align:center;padding:10px 0 26px}.welcome-icon{margin:auto;width:46px;height:46px}.welcome h2{margin:14px 0 8px;font-size:20px}.welcome p{color:#64748b}.quick-list{display:grid;grid-template-columns:1fr 1fr;gap:10px;max-width:620px;margin:22px auto 0}.quick-list button{padding:11px 14px;border:1px solid #dfe5ef;border-radius:9px;background:#fff;color:#475569;cursor:pointer}.quick-list button:hover{border-color:#6688ef;color:#315ecc;background:#f8faff}
.message{display:flex;gap:12px;margin:22px 0}.message.user{flex-direction:row-reverse}.avatar{flex:0 0 34px;height:34px;border-radius:50%;display:grid;place-items:center;background:#eef2ff;color:#4169d8;font-size:12px;font-weight:700}.user .avatar{background:#4169d8;color:#fff}.message-main{max-width:82%}.message-name{font-size:12px;color:#94a3b8;margin-bottom:6px}.user .message-name{text-align:right}.user-text,.assistant-text{padding:12px 15px;border-radius:12px;line-height:1.75}.user-text{background:#4169d8;color:#fff}.assistant-text{background:#f7f9fc;color:#334155}.assistant-text :deep(p){margin:0 0 8px}.assistant-text :deep(p:last-child){margin-bottom:0}.typing{background:#f7f9fc;padding:14px;border-radius:12px}.typing i{display:inline-block;width:6px;height:6px;background:#94a3b8;border-radius:50%;margin:0 2px;animation:pulse 1s infinite}.typing i:nth-child(2){animation-delay:.15s}.typing i:nth-child(3){animation-delay:.3s}@keyframes pulse{50%{opacity:.25}}
.execution-trace{margin-top:7px;color:#64748b;font-size:12px}.execution-trace summary{display:flex;align-items:center;gap:7px;cursor:pointer;list-style:none;width:max-content}.execution-trace summary::-webkit-details-marker{display:none}.execution-trace summary>i{width:7px;height:7px;border-radius:50%;background:#3b82f6;box-shadow:0 0 0 4px #dbeafe}.execution-trace summary>small{color:#94a3b8}.execution-trace[open] summary{margin-bottom:9px}.trace-step{display:grid;grid-template-columns:21px minmax(180px,1fr) auto;align-items:start;gap:8px;padding:5px 0 5px 3px;color:#94a3b8}.trace-step>b{width:18px;height:18px;border-radius:50%;display:grid;place-items:center;background:#edf1f7;font-size:10px}.trace-step span>small{display:block;margin-top:2px;color:#a3acb9}.trace-step em{font-style:normal;font-size:11px}.trace-step.success{color:#16a34a}.trace-step.running{color:#2563eb}.trace-step.failed{color:#dc2626}
.ai-analysis{margin-top:12px;padding:15px 17px;border:1px solid #dfe7fb;border-radius:12px;background:linear-gradient(135deg,#f8faff,#f4f7ff);color:#334155;line-height:1.75}.analysis-title{display:flex;align-items:center;gap:7px;margin-bottom:8px;color:#4169d8}.ai-analysis :deep(p){margin:0 0 8px}.ai-analysis :deep(p:last-child){margin-bottom:0}.follow-up{display:flex;flex-direction:column;align-items:flex-start;gap:8px;margin-top:12px}.follow-up>span{font-size:12px;color:#94a3b8}.follow-up button{max-width:100%;padding:8px 12px;border:1px solid #dbe4f5;border-radius:16px;background:#fff;color:#4169d8;text-align:left;cursor:pointer}.follow-up button:hover{border-color:#6688ef;background:#f5f8ff}
.composer{flex:0 0 auto;padding:14px 22px 12px;border-top:1px solid #edf0f5;display:grid;grid-template-columns:1fr 42px;gap:10px}.composer :deep(.el-textarea__inner){box-shadow:none;border:1px solid #dfe5ef;border-radius:10px;padding:11px}.composer p{grid-column:1/-1;text-align:center;color:#a0a9b8;font-size:11px;margin:0}
.messages{height:0;flex:1 1 0;scrollbar-gutter:stable}
.messages{padding-left:24px;padding-right:24px}
.message{width:min(100%,920px);margin-left:auto;margin-right:auto}
.message.assistant .message-main{width:calc(100% - 46px);max-width:calc(100% - 46px)}
.message.user .message-main{width:auto;max-width:70%}
.composer{width:min(calc(100% - 48px),960px);align-self:center;margin:0 auto;border:1px solid #dfe5ef;border-radius:14px 14px 0 0;background:#fff;box-shadow:0 -4px 18px rgba(30,58,95,.05)}
.composer :deep(.el-textarea__inner){border-color:transparent}
@media(max-width:1100px){.assistant-page{grid-template-columns:240px 1fr}}@media(max-width:760px){.assistant-page{grid-template-columns:1fr}.session-panel{display:none}.messages{padding:20px}.quick-list{grid-template-columns:1fr}}
</style>
