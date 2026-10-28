<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import api, { getErrorMessage, type FinanceResponse } from '../api'

type Preset = 'MONTH' | 'QUARTER' | 'YEAR' | 'CUSTOM'
type DriverSort = 'REVENUE_DESC' | 'REVENUE_ASC' | 'NAME' | 'FREIGHT_DESC'

const today = new Date()
const localDate = (date: Date) => {
  const adjusted = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return adjusted.toISOString().slice(0, 10)
}
const preset = ref<Preset>('MONTH')
const from = ref(localDate(new Date(today.getFullYear(), today.getMonth(), 1)))
const to = ref(localDate(today))
const driverSort = ref<DriverSort>('REVENUE_DESC')
const data = ref<FinanceResponse | null>(null)
const busy = ref(false)
const error = ref('')
const chartMax = computed(() => Math.max(
  1,
  ...(data.value?.trend.flatMap((point) => [point.revenueEur, point.maintenanceCostsEur]) ?? []),
))
const sortedDrivers = computed(() => {
  const drivers = [...(data.value?.drivers ?? [])]
  if (driverSort.value === 'NAME') return drivers.sort((a, b) => a.driverName.localeCompare(b.driverName))
  if (driverSort.value === 'FREIGHT_DESC') {
    return drivers.sort((a, b) => b.deliveredFreightCount - a.deliveredFreightCount)
  }
  const direction = driverSort.value === 'REVENUE_ASC' ? 1 : -1
  return drivers.sort((a, b) => direction * (a.grossContributionEur - b.grossContributionEur))
})
const maintenanceMax = computed(() =>
  Math.max(1, ...(data.value?.maintenanceByCategory.map((item) => item.costEur) ?? [])),
)
const freightMax = computed(() =>
  Math.max(1, ...(data.value?.freightByCategory.map((item) => item.revenueEur) ?? [])),
)

function formatMoney(value: number | null) {
  return value === null
    ? 'Not recorded'
    : new Intl.NumberFormat(undefined, { style: 'currency', currency: 'EUR' }).format(value)
}

function categoryName(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function changePreset(next: Preset) {
  preset.value = next
  const end = new Date()
  const start = new Date(end)
  if (next === 'MONTH') start.setDate(1)
  if (next === 'QUARTER') {
    start.setMonth(Math.floor(start.getMonth() / 3) * 3, 1)
  }
  if (next === 'YEAR') start.setMonth(0, 1)
  if (next !== 'CUSTOM') {
    from.value = localDate(start)
    to.value = localDate(end)
    void loadFinance()
  }
}

async function loadFinance() {
  if (!from.value || !to.value || from.value > to.value) {
    error.value = 'Choose a valid date range.'
    return
  }
  error.value = ''
  busy.value = true
  try {
    const response = await api.get<FinanceResponse>('/finance', {
      params: { from: from.value, to: to.value },
    })
    data.value = response.data
  } catch (requestError) {
    error.value = getErrorMessage(requestError)
  } finally {
    busy.value = false
  }
}

onMounted(() => void loadFinance())
</script>

<template>
  <div class="page-heading finance-heading">
    <div>
      <p class="eyebrow">FINANCE / PERFORMANCE</p>
      <h1>Financial Analysis</h1>
      <p class="subtitle">Delivered freight revenue less recorded vehicle maintenance costs · EUR</p>
    </div>
    <div class="finance-range">
      <div class="finance-presets" aria-label="Finance date range">
        <button v-for="option in (['MONTH', 'QUARTER', 'YEAR', 'CUSTOM'] as const)" :key="option"
          :class="{ selected: preset === option }" @click="changePreset(option)">
          {{ option === 'MONTH' ? 'Month' : option === 'QUARTER' ? 'Quarter' : option === 'YEAR' ? 'Year' : 'Custom' }}
        </button>
      </div>
      <label v-if="preset === 'CUSTOM'">From<input v-model="from" type="date"></label>
      <label v-if="preset === 'CUSTOM'">To<input v-model="to" type="date"></label>
      <button v-if="preset === 'CUSTOM'" class="secondary-button finance-apply" :disabled="busy" @click="loadFinance">
        Apply
      </button>
    </div>
  </div>

  <div v-if="error" class="finance-error" role="alert">{{ error }}</div>
  <div v-if="busy && !data" class="empty-state"><strong>Loading financial records</strong></div>
  <template v-else-if="data">
    <div class="finance-notice">
      <strong>RECORDED DATA ONLY</strong>
      <span>Net profit = delivered freight revenue − recorded maintenance costs. Fuel, wages, tolls, insurance, and other unrecorded costs are excluded. Driver totals are gross freight contribution, not driver profit.</span>
      <span v-if="data.unpricedFreightCount || data.uncostedMaintenanceCount" class="finance-missing">
        {{ data.unpricedFreightCount }} delivered freight item(s) have no revenue recorded;
        {{ data.uncostedMaintenanceCount }} maintenance operation(s) have no cost recorded.
      </span>
      <span class="finance-method">Shipment revenue is dated by route departure. Maintenance is dated by service date. Freight margin allocates recorded maintenance to completed routes by distance, then splits route cost equally across its freight.</span>
    </div>

    <div class="stat-grid finance-stat-grid">
      <article class="stat-card"><div class="stat-top"><span>Freight revenue</span><span class="stat-icon green">€</span></div><strong>{{ formatMoney(data.freightRevenueEur) }}</strong><small>{{ data.deliveredFreightCount }} delivered shipments</small></article>
      <article class="stat-card"><div class="stat-top"><span>Maintenance costs</span><span class="stat-icon orange">−</span></div><strong>{{ formatMoney(data.maintenanceCostsEur) }}</strong><small>Recorded workshop operations</small></article>
      <article class="stat-card"><div class="stat-top"><span>Net profit</span><span class="stat-icon blue">=</span></div><strong :class="data.netProfitEur < 0 ? 'finance-negative' : 'finance-positive'">{{ formatMoney(data.netProfitEur) }}</strong><small>Revenue less recorded maintenance</small></article>
    </div>

    <section class="panel">
      <div class="panel-heading"><div><h2>Revenue &amp; maintenance trend</h2><p>{{ data.from }} to {{ data.to }} · {{ data.trend[0]?.period.length === 10 ? 'daily' : 'monthly' }} totals</p></div><span class="status-pill available">EUR</span></div>
      <div v-if="data.trend.length" class="finance-chart-wrap">
        <div class="finance-chart-legend"><span><i class="revenue-dot"></i> Revenue</span><span><i class="cost-dot"></i> Maintenance</span></div>
        <div class="finance-chart">
          <div v-for="point in data.trend" :key="point.period" class="finance-chart-column">
            <div class="finance-bars">
              <span class="finance-bar revenue-bar" :style="{ height: `${Math.max(2, point.revenueEur / chartMax * 100)}%` }" :title="`Revenue ${formatMoney(point.revenueEur)}`"></span>
              <span class="finance-bar cost-bar" :style="{ height: `${Math.max(2, point.maintenanceCostsEur / chartMax * 100)}%` }" :title="`Maintenance ${formatMoney(point.maintenanceCostsEur)}`"></span>
            </div>
            <small>{{ point.period.length === 10 ? point.period.slice(5) : point.period }}</small>
          </div>
        </div>
      </div>
      <div v-else class="empty-state"><strong>No financial activity for this range</strong></div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div><h2>Driver gross contribution</h2><p>Revenue from delivered freight assigned to each driver; costs are not deducted.</p></div>
        <label class="finance-sort">SORT BY
          <select v-model="driverSort" aria-label="Sort driver financial results">
            <option value="REVENUE_DESC">Revenue: high to low</option>
            <option value="REVENUE_ASC">Revenue: low to high</option>
            <option value="NAME">Driver name</option>
            <option value="FREIGHT_DESC">Delivered shipments</option>
          </select>
        </label>
      </div>
      <div v-if="sortedDrivers.length" class="table-wrap">
        <table><thead><tr><th>DRIVER</th><th>DELIVERED FREIGHT</th><th>GROSS CONTRIBUTION</th></tr></thead>
          <tbody><tr v-for="driver in sortedDrivers" :key="driver.driverId">
            <td><strong>{{ driver.driverName }}</strong></td><td>{{ driver.deliveredFreightCount }}</td><td class="finance-money">{{ formatMoney(driver.grossContributionEur) }}</td>
          </tr></tbody>
        </table>
      </div>
      <div v-else class="empty-state"><strong>No delivered freight revenue recorded</strong></div>
    </section>

    <div class="finance-analysis-grid">
      <section class="panel">
        <div class="panel-heading"><div><h2>Maintenance by component</h2><p>Recorded service costs by maintenance category</p></div></div>
        <div v-if="data.maintenanceByCategory.length" class="finance-breakdown">
          <div v-for="item in data.maintenanceByCategory" :key="item.category" class="finance-breakdown-row">
            <span><strong>{{ categoryName(item.category) }}</strong><small>{{ item.operationCount }} operation(s)</small></span>
            <b>{{ formatMoney(item.costEur) }}</b>
            <i><span :style="{ width: `${item.costEur / maintenanceMax * 100}%` }"></span></i>
          </div>
        </div>
        <div v-else class="empty-state"><strong>No maintenance costs recorded</strong></div>
      </section>
      <section class="panel">
        <div class="panel-heading"><div><h2>Revenue by freight category</h2><p>Delivered shipments grouped by cargo type</p></div></div>
        <div v-if="data.freightByCategory.length" class="finance-breakdown">
          <div v-for="item in data.freightByCategory" :key="item.category" class="finance-breakdown-row">
            <span><strong>{{ categoryName(item.category) }}</strong><small>{{ item.shipmentCount }} shipment(s)</small></span>
            <b>{{ formatMoney(item.revenueEur) }}</b>
            <i><span class="freight-breakdown-bar" :style="{ width: `${item.revenueEur / freightMax * 100}%` }"></span></i>
          </div>
        </div>
        <div v-else class="empty-state"><strong>No freight revenue recorded</strong></div>
      </section>
    </div>

    <section class="panel">
      <div class="panel-heading"><div><h2>Vehicle profitability</h2><p>Freight revenue attributed to vehicle less that vehicle’s recorded maintenance costs.</p></div></div>
      <div v-if="data.vehicles.length" class="table-wrap">
        <table><thead><tr><th>VEHICLE</th><th>FREIGHT REVENUE</th><th>MAINTENANCE</th><th>NET</th></tr></thead>
          <tbody><tr v-for="vehicle in data.vehicles" :key="vehicle.vehicleId">
            <td><strong>{{ vehicle.licensePlate }}</strong></td><td>{{ formatMoney(vehicle.freightRevenueEur) }}</td><td>{{ formatMoney(vehicle.maintenanceCostsEur) }}</td><td class="finance-money">{{ formatMoney(vehicle.netProfitEur) }}</td>
          </tr></tbody>
        </table>
      </div>
      <div v-else class="empty-state"><strong>No vehicle financial activity</strong></div>
    </section>

    <section class="panel">
      <div class="panel-heading"><div><h2>Estimated freight margins</h2><p>Maintenance allocation is an estimate based on route distance, not a shipment-level invoice.</p></div></div>
      <div v-if="data.freightMargins.length" class="table-wrap">
        <table><thead><tr><th>FREIGHT</th><th>CATEGORY</th><th>DRIVER / VEHICLE</th><th>REVENUE</th><th>ALLOCATED MAINTENANCE</th><th>EST. MARGIN</th></tr></thead>
          <tbody><tr v-for="item in data.freightMargins" :key="item.freightId">
            <td><strong>{{ item.trackingCode }}</strong><small>{{ item.clientName }}</small></td>
            <td>{{ categoryName(item.category) }}</td>
            <td>{{ item.driverName }}<small>{{ item.vehicleLicensePlate }}</small></td>
            <td>{{ formatMoney(item.revenueEur) }}</td>
            <td>{{ formatMoney(item.estimatedMaintenanceEur) }}</td>
            <td class="finance-money">{{ formatMoney(item.estimatedMarginEur) }}<small v-if="item.estimatedMarginPercent !== null">{{ item.estimatedMarginPercent }}% margin</small></td>
          </tr></tbody>
        </table>
      </div>
      <div v-else class="empty-state"><strong>No completed freight in this date range</strong></div>
    </section>
  </template>
</template>
