<template>
  <div>
    <div style="margin-bottom:16px">
      <el-button @click="loadDevices">刷新</el-button>
      <span style="margin-left:16px;color:#666">在线设备: {{ onlineCount }} 台</span>
    </div>

    <el-row :gutter="16">
      <el-col v-for="d in devices" :key="d.id" :span="8" style="margin-bottom:16px">
        <el-card shadow="hover">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>{{ d.deviceName || '未知设备' }}</span>
              <el-tag :type="d.status === 1 ? 'success' : d.status === 2 ? 'warning' : 'info'" size="small">
                {{ d.status === 0 ? '离线' : d.status === 1 ? '在线' : '执行中' }}
              </el-tag>
            </div>
          </template>
          <p><strong>UUID:</strong> {{ d.deviceUuid }}</p>
          <p><strong>分辨率:</strong> {{ d.resolution || '未知' }}</p>
          <p><strong>最后在线:</strong> {{ d.lastOnline || '—' }}</p>
          <p><strong>注册时间:</strong> {{ d.createTime || '—' }}</p>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="devices.length === 0 && !loading" description="暂无设备连接" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getDeviceList } from '../api'

const devices = ref([])
const loading = ref(false)

const onlineCount = computed(() => devices.value.filter(d => d.status >= 1).length)

const loadDevices = async () => {
  loading.value = true
  try {
    const res = await getDeviceList(1, 50)
    devices.value = res.data.records || []
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

onMounted(loadDevices)
</script>
