import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 响应拦截：统一提取data
api.interceptors.response.use(
  res => res.data,
  err => {
    console.error('API请求失败:', err)
    return Promise.reject(err)
  }
)

// ==================== 设备API ====================

/** 设备列表 */
export const getDeviceList = (pageNum = 1, pageSize = 20) =>
  api.get('/device/list', { params: { pageNum, pageSize } })

/** 在线设备 */
export const getOnlineDevices = () => api.get('/device/online')

/** 设备详情 */
export const getDeviceDetail = (deviceUuid) =>
  api.get(`/device/${deviceUuid}`)

// ==================== 任务API ====================

/** 任务列表 */
export const getTaskList = (pageNum = 1, pageSize = 20, status) =>
  api.get('/task/list', { params: { pageNum, pageSize, status } })

/** 任务详情 */
export const getTaskDetail = (taskId) => api.get(`/task/${taskId}`)

/** 任务步骤明细 */
export const getTaskSteps = (taskId) => api.get(`/task/${taskId}/steps`)

/** 创建并执行任务（SSE流式） */
export const executeTask = (deviceUuid, instruction) => {
  return fetch('/api/agent/execute', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceUuid, instruction })
  })
}

/** 取消任务 */
export const cancelTask = (taskId) => api.post(`/task/${taskId}/cancel`)
