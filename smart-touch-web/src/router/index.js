import { createRouter, createWebHashHistory } from 'vue-router'
import TaskView from '../views/TaskView.vue'
import DeviceView from '../views/DeviceView.vue'
import TaskDetail from '../views/TaskDetail.vue'

const routes = [
  { path: '/', redirect: '/tasks' },
  { path: '/tasks', component: TaskView, name: 'tasks' },
  { path: '/tasks/:taskId', component: TaskDetail, name: 'taskDetail' },
  { path: '/devices', component: DeviceView, name: 'devices' }
]

export default createRouter({
  history: createWebHashHistory(),
  routes
})
