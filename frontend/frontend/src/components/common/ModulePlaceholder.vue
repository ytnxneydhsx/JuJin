<script setup lang="ts">
defineProps<{
  title: string
  description: string
}>()
</script>

<template>
  <section class="placeholder">
    <h1 class="placeholder-title">{{ title }}</h1>
    <p class="placeholder-description">{{ description }}</p>
  </section>
</template>
