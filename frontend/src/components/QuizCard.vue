<template>
  <el-card class="quiz-card" shadow="hover">
    <div class="question">{{ index }}. {{ item.question }}</div>
    <el-radio-group v-model="selected" :disabled="answered">
      <el-radio v-for="(opt, i) in item.options" :key="i" :label="opt">
        {{ String.fromCharCode(65 + i) }}. {{ opt }}
      </el-radio>
    </el-radio-group>
    <div v-if="answered" class="result" :class="isCorrect ? 'correct' : 'wrong'">
      {{ isCorrect ? '✓ 正确' : '✗ 错误，正确答案：' + item.answer }}
      <div class="explanation">{{ item.explanation }}</div>
    </div>
    <el-button v-if="!answered" type="primary" size="small" @click="submit" :disabled="!selected">
      提交
    </el-button>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  item: { type: Object, required: true },
  index: { type: Number, default: 1 }
})

const emit = defineEmits(['submit'])

const selected = ref('')
const answered = ref(false)
const isCorrect = ref(false)

function submit() {
  answered.value = true
  isCorrect.value = selected.value === props.item.answer
  emit('submit', { id: props.item.id, answer: selected.value, correct: isCorrect.value })
}
</script>

<style scoped>
.quiz-card { margin-bottom: 16px; }
.question { font-weight: bold; margin-bottom: 12px; line-height: 1.6; }
.result { margin-top: 12px; padding: 8px; border-radius: 4px; font-size: 13px; }
.correct { background: #f0f9eb; color: #67c23a; }
.wrong { background: #fef0f0; color: #f56c6c; }
.explanation { margin-top: 6px; color: #666; font-size: 12px; }
</style>