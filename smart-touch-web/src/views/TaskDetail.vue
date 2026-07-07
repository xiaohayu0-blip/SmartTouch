<template>
  <div>
    <el-button @click="$router.back()" style="margin-bottom:16px">← 返回</el-button>

    <el-descriptions v-if="task" :column="2" border style="margin-bottom:24px">
      <el-descriptions-item label="任务编号">{{ task.taskNo }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag :type="statusType(task.status)">{{ statusLabel(task.status) }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="用户指令" :span="2">{{ task.instruction }}</el-descriptions-item>
      <el-descriptions-item label="执行步数">{{ task.currentStep }} / {{ task.totalSteps }}</el-descriptions-item>
      <el-descriptions-item label="结果">{{ task.resultMsg || '—' }}</el-descriptions-item>
      <el-descriptions-item label="开始时间">{{ task.startTime || '—' }}</el-descriptions-item>
      <el-descriptions-item label="结束时间">{{ task.endTime || '—' }}</el-descriptions-item>
    </el-descriptions>

    <h3>步骤明细（步骤回放）</h3>
    <el-timeline v-if="steps.length > 0">
      <el-timeline-item v-for="s in steps" :key="s.stepNo"
                        :timestamp="`Step ${s.stepNo} | 耗时 ${s.costMs}ms`"
                        :color="s.result === 'success' ? '#67c23a' : '#f56c6c'"
                        placement="top">
        <el-card shadow="never">
          <p><strong>动作:</strong> {{ s.action }}
            <el-tag size="small">{{ s.result }}</el-tag>
          </p>
          <p><strong>参数:</strong> <code>{{ s.actionJson }}</code></p>
          <p><strong>LLM推理:</strong> {{ s.llmReason }}</p>
          <p v-if="s.screenshotUrl" style="color:#999;font-size:12px">
            截图已保存
          </p>
        </el-card>
      </el-timeline-item>
    </el-timeline>

    <el-empty v-if="!loading && steps.length === 0" description="暂无步骤数据" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getTaskDetail, getTaskSteps } from '../api'

const route = useRoute()
const task = ref(null)
const steps = ref([])
const loading = ref(false)

const statusLabel = (s) => ['待执行','执行中','成功','失败','超时','已取消'][s] || '未知'
const statusType = (s) => ['info','warning','success','danger','danger','info'][s] || 'info'

onMounted(async () => {
  loading.value = true
  try {
    const [res1, res2] = await Promise.all([
      getTaskDetail(route.params.taskId),
      getTaskSteps(route.params.taskId)
    ])
    task.value = res1.data
    steps.value = res2.data || []
  } catch (e) { console.error(e) }
  finally { loading.value = false }
})
</script>
