<template>
  <el-container style="min-height:100vh">
    <el-header style="background:#409eff;color:#fff;display:flex;align-items:center;padding:0 24px">
      <h2 style="margin:0">SmartTouch 管理后台</h2>
      <el-menu mode="horizontal" :default-active="route.path"
               style="margin-left:48px;border-bottom:none;background:transparent"
               router text-color="#fff" active-text-color="#ffd04b">
        <el-menu-item index="/tasks">任务管理</el-menu-item>
        <el-menu-item index="/devices">设备监控</el-menu-item>
      </el-menu>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
