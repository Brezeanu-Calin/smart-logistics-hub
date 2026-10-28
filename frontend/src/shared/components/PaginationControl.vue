<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  currentPage: number
  pageCount: number
  total: number
  pageSize?: number
}>(), {
  pageSize: 10,
})

const emit = defineEmits<{
  'update:currentPage': [page: number]
  'update:pageSize': [pageSize: number]
}>()

const firstItem = computed(() => props.total === 0 ? 0 : (props.currentPage - 1) * props.pageSize + 1)
const lastItem = computed(() => Math.min(props.total, props.currentPage * props.pageSize))

function goTo(page: number) {
  emit('update:currentPage', Math.max(1, Math.min(props.pageCount, page)))
}
</script>

<template>
  <nav class="pagination-control" aria-label="Pagination">
    <span>Showing {{ firstItem }}–{{ lastItem }} of {{ total }}</span>
    <div>
      <label>Rows
        <select :value="pageSize" aria-label="Rows per page" @change="emit('update:pageSize', Number(($event.target as HTMLSelectElement).value))">
          <option value="10">10</option>
          <option value="25">25</option>
          <option value="50">50</option>
          <option value="100">100</option>
        </select>
      </label>
      <button :disabled="currentPage <= 1" aria-label="Previous page" @click="goTo(currentPage - 1)">←</button>
      <strong>Page {{ currentPage }} of {{ pageCount }}</strong>
      <button :disabled="currentPage >= pageCount" aria-label="Next page" @click="goTo(currentPage + 1)">→</button>
    </div>
  </nav>
</template>
