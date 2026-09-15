<template>
  <div class="code-block">
    <div class="header">
      <span class="lang">{{ language }}</span>
      <button class="copy-btn" @click="copy">复制</button>
    </div>
    <pre class="code"><code>{{ code }}</code></pre>
  </div>
</template>

<script setup>
const props = defineProps({
  language: { type: String, default: 'python' },
  code: { type: String, default: '' }
})

function copy() {
  navigator.clipboard.writeText(props.code || '').catch(() => {})
}
</script>

<style scoped>
.code-block { background: #f6f8fa; border-radius: 6px; overflow: hidden; }
.header { display: flex; justify-content: space-between; align-items: center; padding: 6px 12px; background: #e8e8e8; font-size: 12px; }
.lang { font-weight: bold; }
.copy-btn { border: none; background: #409eff; color: white; border-radius: 4px; padding: 2px 8px; cursor: pointer; font-size: 12px; }
.code { margin: 0; padding: 12px; overflow-x: auto; font-size: 13px; line-height: 1.5; }
</style>