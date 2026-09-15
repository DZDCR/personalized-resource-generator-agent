<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/">对话学习</el-menu-item>
        <el-menu-item index="/resources">学习资源</el-menu-item>
        <el-menu-item index="/path">学习路径</el-menu-item>
        <el-menu-item index="/dashboard">效果评估</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
</script>

<style>
.layout { height: 100vh; }
.aside { border-right: 1px solid #eee; }
</style>