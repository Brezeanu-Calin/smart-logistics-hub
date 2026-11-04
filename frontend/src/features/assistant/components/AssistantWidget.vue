<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import api, {
  getErrorMessage,
  type AssistantAction,
  type AssistantChatMessage,
  type AssistantConfig,
  type Driver,
  type FinanceResponse,
  type Goods,
  type Route,
  type Trailer,
  type Vehicle,
} from '../../../app/api'

const emit = defineEmits<{ dataChanged: [] }>()
const MAX_OPERATIONAL_CONTEXT_BYTES = 150_000

type ChatEntry = AssistantChatMessage & { action?: AssistantAction }

const isOpen = ref(false)
const busy = ref(false)
const draft = ref('')
const error = ref('')
const config = ref<AssistantConfig | null>(null)
const messages = ref<ChatEntry[]>([{
  role: 'assistant',
  content: 'Ask about live logistics operations, or describe a change. I will show a proposal for your confirmation before writing anything.',
}])
const pendingAction = ref<AssistantAction | null>(null)
const messageLog = ref<HTMLElement | null>(null)

watch(messages, async () => {
  await nextTick()
  if (messageLog.value) messageLog.value.scrollTop = messageLog.value.scrollHeight
}, { deep: true })

onMounted(async () => {
  try {
    const response = await api.get<AssistantConfig>('/assistant/config')
    config.value = response.data
  } catch (requestError) {
    error.value = `Could not load assistant configuration: ${getErrorMessage(requestError)}`
  }
})

function localDate(date: Date) {
  const adjusted = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return adjusted.toISOString().slice(0, 10)
}

async function loadContext() {
  const today = new Date()
  const from = localDate(new Date(today.getFullYear(), today.getMonth(), 1))
  const to = localDate(today)
  const [driversResponse, vehiclesResponse, trailersResponse, goodsResponse, routesResponse, financeResponse] =
    await Promise.all([
      api.get<Driver[]>('/drivers'),
      api.get<Vehicle[]>('/vehicles'),
      api.get<Trailer[]>('/trailers'),
      api.get<Goods[]>('/goods'),
      api.get<Route[]>('/routes'),
      api.get<FinanceResponse>('/finance', { params: { from, to } }),
    ])

  return {
    drivers: driversResponse.data.map(({ id, name, licenseCategories, status }) =>
      ({ id, name, licenseCategories, status })),
    vehicles: vehiclesResponse.data,
    trailers: trailersResponse.data,
    freight: goodsResponse.data,
    routes: routesResponse.data,
    finance: financeResponse.data,
    financePeriod: { from, to },
  }
}

async function sendMessage() {
  const content = draft.value.trim()
  if (!content || busy.value || pendingAction.value || !config.value) return
  error.value = ''
  draft.value = ''
  pendingAction.value = null
  messages.value.push({ role: 'user', content })
  busy.value = true
  try {
    const context = await loadContext()
    if (context === null || typeof context !== 'object' || Array.isArray(context)
      || new TextEncoder().encode(JSON.stringify(context)).byteLength > MAX_OPERATIONAL_CONTEXT_BYTES) {
      throw new Error('Operational context must be a JSON object no larger than 150 KB.')
    }
    const history = messages.value
      .filter((message) => message.role === 'user' || message.role === 'assistant')
      .slice(-20)
      .map(({ role, content: messageContent }) => ({ role, content: messageContent }))
    const response = await api.post<{
      message: string
      action: AssistantAction | null
    }>('/assistant/chat', { messages: history, context })
    messages.value.push({
      role: 'assistant',
      content: response.data.message,
      action: response.data.action ?? undefined,
    })
    pendingAction.value = response.data.action
  } catch (requestError) {
    error.value = getErrorMessage(requestError)
  } finally {
    busy.value = false
  }
}

function recordId(action: AssistantAction) {
  if (action.id === null || !Number.isSafeInteger(action.id) || action.id <= 0) {
    throw new Error('The proposed change is missing a valid record ID.')
  }
  return action.id
}

function payloadObject(value: unknown, field: string) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`The proposed change is missing its ${field} details.`)
  }
  return value as Record<string, unknown>
}

function routeWithGoodsId(routeValue: unknown, goodsId: number) {
  const route = payloadObject(routeValue, 'route')
  if (!Array.isArray(route.stops) || route.stops.length === 0) {
    throw new Error('The proposed route must include ordered pickup and delivery stops.')
  }
  const stops = route.stops.map((stopValue) => {
    const stop = payloadObject(stopValue, 'route stop')
    const requestedGoodsId = stop.goodsId
    if (typeof requestedGoodsId !== 'number' || !Number.isSafeInteger(requestedGoodsId) || requestedGoodsId < 0
      || (stop.type !== 'PICKUP' && stop.type !== 'DELIVERY')) {
      throw new Error('The proposed route contains an invalid stop.')
    }
    return { goodsId: requestedGoodsId === 0 ? goodsId : requestedGoodsId, type: stop.type }
  })
  return { ...route, stops }
}

async function executeAction(action: AssistantAction) {
  const payload = action.payload
  switch (action.type) {
    case 'CREATE_DRIVER':
      await api.post('/drivers', payload)
      break
    case 'UPDATE_DRIVER':
      await api.put(`/drivers/${recordId(action)}`, payload)
      break
    case 'DELETE_DRIVER':
      await api.delete(`/drivers/${recordId(action)}`)
      break
    case 'SET_DRIVER_STATUS':
      await api.patch(`/drivers/${recordId(action)}/status`, payload)
      break
    case 'CREATE_VEHICLE':
      await api.post('/vehicles', payload)
      break
    case 'UPDATE_VEHICLE':
      await api.put(`/vehicles/${recordId(action)}`, payload)
      break
    case 'DELETE_VEHICLE':
      await api.delete(`/vehicles/${recordId(action)}`)
      break
    case 'SET_VEHICLE_STATUS':
      await api.patch(`/vehicles/${recordId(action)}/status`, payload)
      break
    case 'RECORD_MAINTENANCE':
      await api.post(`/vehicles/${recordId(action)}/maintenance`, payload)
      break
    case 'CREATE_TRAILER':
      await api.post('/trailers', payload)
      break
    case 'UPDATE_TRAILER':
      await api.put(`/trailers/${recordId(action)}`, payload)
      break
    case 'DELETE_TRAILER':
      await api.delete(`/trailers/${recordId(action)}`)
      break
    case 'SET_TRAILER_STATUS':
      await api.patch(`/trailers/${recordId(action)}/status`, payload)
      break
    case 'CREATE_FREIGHT':
      await api.post('/goods', payload)
      break
    case 'UPDATE_FREIGHT':
      await api.put(`/goods/${recordId(action)}`, payload)
      break
    case 'DELETE_FREIGHT':
      await api.delete(`/goods/${recordId(action)}`)
      break
    case 'CREATE_ROUTE':
      await api.post('/routes', payload)
      break
    case 'DELETE_ROUTE':
      await api.delete(`/routes/${recordId(action)}`)
      break
    case 'UPDATE_ROUTE_STATUS':
      await api.patch(`/routes/${recordId(action)}/status`, payload)
      break
    case 'DELIVER_FREIGHT': {
      const goodsId = Number(payload.goodsId)
      if (!Number.isSafeInteger(goodsId) || goodsId <= 0) {
        throw new Error('The delivery proposal is missing a valid freight ID.')
      }
      await api.patch(`/routes/${recordId(action)}/goods/${goodsId}/deliver`)
      break
    }
    case 'CREATE_FREIGHT_AND_ROUTE': {
      const freight = payloadObject(payload.freight, 'freight')
      const route = payloadObject(payload.route, 'route')
      const created = await api.post<Goods>('/goods', freight)
      try {
        await api.post('/routes', routeWithGoodsId(route, created.data.id))
      } catch (requestError) {
        emit('dataChanged')
        throw new Error(
          `Freight ${created.data.trackingCode} was created, but route planning failed: ${getErrorMessage(requestError)}. The freight remains unassigned.`,
        )
      }
      break
    }
    default:
      throw new Error('This proposed operation is not supported.')
  }
}

async function confirmAction() {
  const action = pendingAction.value
  if (!action || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await executeAction(action)
    emit('dataChanged')
    messages.value.push({ role: 'assistant', content: 'Confirmed change completed successfully.' })
    pendingAction.value = null
  } catch (requestError) {
    error.value = getErrorMessage(requestError)
  } finally {
    busy.value = false
  }
}

function cancelAction() {
  if (pendingAction.value) {
    messages.value.push({ role: 'assistant', content: 'Proposed change cancelled; no write was made.' })
    pendingAction.value = null
  }
}

function clearConversation() {
  if (busy.value) return
  messages.value = [{
    role: 'assistant',
    content: 'Ask about live logistics operations, or describe a change. I will show a proposal for your confirmation before writing anything.',
  }]
  pendingAction.value = null
  error.value = ''
}
</script>

<template>
  <div class="assistant-widget">
    <button
      v-if="!isOpen"
      class="assistant-launcher"
      type="button"
      aria-label="Open logistics assistant"
      @click="isOpen = true"
    >
      <span aria-hidden="true">✦</span> Assistant
    </button>

    <section v-else class="assistant-panel" role="dialog" aria-modal="false" aria-labelledby="assistant-title">
      <header class="assistant-header">
        <div>
          <strong id="assistant-title">Haulmaster assistant</strong>
          <small v-if="config">{{ config.model }} · {{ config.cloudModel ? 'Cloud model' : 'Local model' }}</small>
          <small v-else>Connecting to Ollama…</small>
        </div>
        <div class="assistant-header-actions">
          <button type="button" aria-label="Clear conversation" :disabled="busy" @click="clearConversation">↻</button>
          <button type="button" aria-label="Close assistant" @click="isOpen = false">×</button>
        </div>
      </header>

      <p v-if="config?.cloudModel" class="assistant-cloud-notice">
        This model runs in Ollama Cloud. Prompts and the live operational context used to answer them are sent to Ollama.
      </p>

      <div ref="messageLog" class="assistant-messages" role="log" aria-live="polite">
        <article
          v-for="(message, index) in messages"
          :key="`${index}-${message.role}`"
          class="assistant-message"
          :class="message.role"
        >
          <p>{{ message.content }}</p>
        </article>
        <p v-if="busy" class="assistant-thinking" role="status">Checking live operations…</p>
      </div>

      <section v-if="pendingAction" class="assistant-proposal" aria-label="Proposed change">
        <strong>Confirm before writing</strong>
        <p>{{ pendingAction.summary }}</p>
        <small v-if="pendingAction.id !== null">Target record ID #{{ pendingAction.id }}</small>
        <pre>{{ JSON.stringify(pendingAction.payload, null, 2) }}</pre>
        <div>
          <button type="button" class="assistant-confirm" :disabled="busy" @click="confirmAction">Confirm change</button>
          <button type="button" :disabled="busy" @click="cancelAction">Cancel</button>
        </div>
      </section>

      <p v-if="error" class="assistant-error" role="alert">{{ error }}</p>
      <form class="assistant-compose" @submit.prevent="sendMessage">
        <textarea
          v-model="draft"
          aria-label="Message the logistics assistant"
          placeholder="Ask about operations or describe a change…"
          rows="2"
          maxlength="8000"
          :disabled="busy || Boolean(pendingAction) || !config"
          @keydown.enter.exact.prevent="sendMessage"
        />
        <button type="submit" :disabled="busy || Boolean(pendingAction) || !config || !draft.trim()">Send</button>
      </form>
    </section>
  </div>
</template>

<style scoped>
.assistant-widget { position: fixed; right: 24px; bottom: 24px; z-index: 1200; font-size: 13px; }
.assistant-launcher { min-height: 48px; border: 0; border-radius: 24px; padding: 0 20px; color: #fff; background: #635bdb; box-shadow: 0 5px 20px #20233a33; font-weight: 700; }
.assistant-launcher span { margin-right: 7px; }
.assistant-panel { display: flex; width: min(420px, calc(100vw - 28px)); height: min(650px, calc(100vh - 40px)); flex-direction: column; overflow: hidden; border: 1px solid var(--line); border-radius: 0; color: var(--text); background: var(--surface); box-shadow: 0 14px 44px #0008; }
.assistant-header { display: flex; min-height: 62px; align-items: center; justify-content: space-between; padding: 10px 15px; color: #fff; background: #13213c; }
.assistant-header strong, .assistant-header small { display: block; }
.assistant-header strong { font-size: 13px; }
.assistant-header small { margin-top: 3px; color: #bac5d8; font-size: 10px; }
.assistant-header-actions { display: flex; gap: 5px; }
.assistant-header-actions button { width: 30px; height: 30px; border: 1px solid #ffffff3d; border-radius: 5px; color: #fff; background: transparent; font-size: 17px; }
.assistant-cloud-notice { margin: 0; border-bottom: 1px solid #80520e; padding: 8px 12px; color: #fbbf24; background: #241d10; font-size: 10px; line-height: 1.45; }
.assistant-messages { display: flex; flex: 1; flex-direction: column; gap: 10px; overflow-y: auto; padding: 14px; background: var(--asphalt); }
.assistant-message { max-width: 90%; border: 1px solid var(--line); border-radius: 0; padding: 9px 11px; color: var(--text); background: var(--surface-raised); line-height: 1.5; white-space: pre-wrap; overflow-wrap: anywhere; }
.assistant-message p { margin: 0; }
.assistant-message.user { align-self: flex-end; border-color: #80520e; color: var(--text); background: #282114; }
.assistant-thinking { margin: 0; color: var(--muted); font-size: 11px; }
.assistant-proposal { max-height: 220px; overflow-y: auto; border-top: 1px solid #80520e; padding: 10px 13px; background: #211a0d; }
.assistant-proposal > strong { color: var(--yellow); font-size: 11px; }
.assistant-proposal > p { margin: 5px 0; color: var(--text); font-size: 11px; line-height: 1.45; }
.assistant-proposal pre { max-height: 95px; overflow: auto; margin: 6px 0 9px; color: var(--muted); font-size: 9px; white-space: pre-wrap; overflow-wrap: anywhere; }
.assistant-proposal > div { display: flex; gap: 7px; }
.assistant-proposal button, .assistant-compose button { min-height: 32px; border: 1px solid var(--line); border-radius: 0; padding: 0 11px; color: var(--text); background: var(--surface-raised); font-size: 10px; font-weight: 650; }
.assistant-proposal .assistant-confirm { border-color: var(--yellow); color: var(--asphalt); background: var(--yellow); }
.assistant-error { margin: 0; border-top: 1px solid #7f1d1d; padding: 8px 12px; color: #fca5a5; background: #261314; font-size: 10px; overflow-wrap: anywhere; }
.assistant-compose { display: flex; align-items: flex-end; gap: 8px; border-top: 1px solid var(--line); padding: 10px; background: var(--surface); }
.assistant-compose textarea { flex: 1; min-height: 42px; max-height: 100px; resize: vertical; border: 1px solid #374151; border-radius: 0; outline: none; padding: 8px; color: var(--text); background: #111416; font: inherit; font-size: 11px; }
.assistant-compose textarea::placeholder { color: var(--muted); }
.assistant-compose textarea:focus { border-color: var(--yellow); }
.assistant-compose button { min-width: 52px; border-color: var(--yellow); color: var(--asphalt); background: var(--yellow); }
@media (max-width: 600px) {
  .assistant-widget { right: 12px; bottom: 76px; }
  .assistant-panel { width: calc(100vw - 24px); height: min(72vh, 650px); }
}
</style>
