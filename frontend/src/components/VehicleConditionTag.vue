<script setup lang="ts">
import { computed } from 'vue'

import type { LatestState } from '@/types/vehicle'
import { vehicleCondition } from '@/utils/vehicle'

const props = defineProps<{
  state: LatestState | null
}>()

const condition = computed(() => vehicleCondition(props.state))
</script>

<template>
  <span class="condition-tag" :style="{ color: condition.color }">
    <span class="condition-dot" :style="{ backgroundColor: condition.color }" />
    {{ condition.label }}
  </span>
</template>

<style scoped>
.condition-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 62px;
  font-size: 13px;
  font-weight: 600;
}

.condition-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
</style>
