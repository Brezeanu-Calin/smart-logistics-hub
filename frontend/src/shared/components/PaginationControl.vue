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
      <button :disabled="currentPage <= 1" aria-label="Previous page" @click="goTo(currentPage - 1)">←</button>
      <strong>Page {{ currentPage }} of {{ pageCount }}</strong>
      <button :disabled="currentPage >= pageCount" aria-label="Next page" @click="goTo(currentPage + 1)">→</button>
    </div>
  </nav>
</template>
