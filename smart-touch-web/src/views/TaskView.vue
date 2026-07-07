<template>
  <div>
    <!-- 新建任务弹窗 -->
    <el-dialog v-model="dialogVisible" title="新建任务" width="520px">
      <el-form :model="taskForm" label-width="100px">
        <el-form-item label="目标设备">
          <el-select v-model="taskForm.deviceUuid" placeholder="选择在线设备" style="width:100%">
            <el-option v-for="d in onlineDevices" :key="d.deviceUuid"
                       :label="`${d.deviceName || d.deviceUuid} (${d.resolution})`"
                       :value="d.deviceUuid" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务指令">
          <el-input v-model="taskForm.instruction" type="textarea" :rows="3"
                    placeholder="如：打开微信给张三发你好" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTask" :loading="executing">
          开始执行
        </el-button>
      </template>
    </el-dialog>

    <!-- 执行进度弹窗 -->
    <el-dialog v-model="progressVisible" title="任务执行中" width="500px" :close-on-click-modal="false">
      <div v-if="executing" style="text-align:center;padding:20px">
        <el-progress :percentage="Math.min(progressSteps * 3, 90)" :stroke-width="16" />
        <p style="margin-top:16px;color:#666">
          已执行 {{ progressSteps }} 步
          <template v-if="lastStep">— {{ lastStep.action }}: {{ lastStep.reason }}</template>
        </p>
      </div>
    </el-dialog>

    <!-- 工具栏 -->
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
      <div>
        <el-button type="primary" @click="openCreate">+ 新建任务</el-button>
        <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width:140px;margin-left:12px"
                   @change="loadTasks">
          <el-option label="待执行" :value="0" />
          <el-option label="执行中" :value="1" />
          <el-option label="成功" :value="2" />
          <el-option label="失败" :value="3" />
          <el-option label="超时" :value="4" />
          <el-option label="已取消" :value="5" />
        </el-select>
      </div>
      <el-button @click="loadTasks">刷新</el-button>
    </div>

    <!-- 任务列表表格 -->
    <el-table :data="tasks" stripe v-loading="loading" @row-click="goDetail" style="cursor:pointer">
      <el-table-column prop="taskNo" label="任务编号" width="200" />
      <el-table-column prop="instruction" label="指令" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currentStep" label="当前步数" width="90" />
      <el-table-column prop="totalSteps" label="总步数" width="80" />
      <el-table-column prop="resultMsg" label="结果" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button size="small" @click.stop="goDetail(row)">详情</el-button>
          <el-button v-if="row.status === 0 || row.status === 1"
                     size="small" type="danger" @click.stop="cancel(row.id)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top:16px;justify-content:flex-end"
                   v-model:current-page="pageNum" :page-size="pageSize"
                   :total="total" @current-change="loadTasks" layout="prev,pager,next,total" />
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { getTaskList, getOnlineDevices, executeTask, cancelTask } from '../api'

const router = useRouter()
const tasks = ref([])
const onlineDevices = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const progressVisible = ref(false)
const executing = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const statusFilter = ref(null)
const progressSteps = ref(0)
const lastStep = ref(null)

const taskForm = ref({ deviceUuid: '', instruction: '' })

const statusLabel = (s) => ['待执行','执行中','成功','失败','超时','已取消'][s] || '未知'
const statusType = (s) => ['info','warning','success','danger','danger','info'][s] || 'info'

const loadTasks = async () => {
  loading.value = true
  try {
    const res = await getTaskList(pageNum.value, pageSize.value, statusFilter.value)
    tasks.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

const openCreate = async () => {
  dialogVisible.value = true
  try {
    const res = await getOnlineDevices()
    onlineDevices.value = res.data || []
  } catch (e) { console.error(e) }
}

const submitTask = async () => {
  if (!taskForm.value.deviceUuid || !taskForm.value.instruction) {
    ElMessage.warning('请填写设备和指令')
    return
  }
  executing.value = true
  progressSteps.value = 0
  lastStep.value = null
  dialogVisible.value = false
  progressVisible.value = true

  try {
    const response = await executeTask(taskForm.value.deviceUuid, taskForm.value.instruction)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      const lines = buffer.split('\n')
      buffer = lines.pop()
      for (const line of lines) {
        if (line.startsWith('event:step') || line.startsWith('data:')) {
          try {
            const json = JSON.parse(line.startsWith('data:') ? line.slice(5) : '')
            if (json.stepNo) {
              progressSteps.value = json.stepNo
              lastStep.value = json
            }
          } catch (e) {}
        }
      }
    }
    ElMessage.success('任务执行完成')
  } catch (e) {
    ElMessage.error('执行失败: ' + e.message)
  } finally {
    executing.value = false
    progressVisible.value = false
    loadTasks()
  }
}

const goDetail = (row) => router.push(`/tasks/${row.id}`)
const cancel = async (taskId) => {
  try {
    await cancelTask(taskId)
    ElMessage.success('已取消')
    loadTasks()
  } catch (e) {
    ElMessage.error('取消失败')
  }
}

onMounted(loadTasks)
</script>
