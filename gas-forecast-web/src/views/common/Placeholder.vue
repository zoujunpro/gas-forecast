<template>
  <section class="placeholder-page">
    <div class="summary-panel">
      <div>
        <p class="eyebrow">{{ moduleName }}</p>
        <h1>{{ title }}</h1>
        <p>{{ description }}</p>
      </div>
      <el-tag type="info" effect="plain">静态占位</el-tag>
    </div>

    <div class="layout-grid">
      <div class="section-panel">
        <div class="panel-head">
          <h2>页面定位</h2>
          <span>01</span>
        </div>
        <ul>
          <li v-for="item in scopes" :key="item">{{ item }}</li>
        </ul>
      </div>

      <div class="section-panel">
        <div class="panel-head">
          <h2>后续接入</h2>
          <span>02</span>
        </div>
        <ul>
          <li>接入列表查询、分页、筛选和排序</li>
          <li>补充新增、编辑、删除和导入导出操作</li>
          <li>根据角色权限控制菜单和按钮可见性</li>
        </ul>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const pageMap: Record<string, { module: string; title: string; description: string; scopes: string[] }> = {
  '/system/logs': {
    module: '系统管理',
    title: '操作日志',
    description: '查看用户操作、接口调用、异常记录和审计轨迹。',
    scopes: ['操作人和操作时间', '操作模块和接口路径', '异常信息和审计导出']
  },
  '/system/settings': {
    module: '系统管理',
    title: '系统参数',
    description: '维护平台运行参数、默认模型参数和业务开关。',
    scopes: ['基础运行参数', '预测默认参数', '系统开关和通知策略']
  }
}

const page = computed(
  () =>
    pageMap[route.path] || {
      module: '平台功能',
      title: '功能页面',
      description: '当前页面为静态菜单占位，后续接入具体数据和操作。',
      scopes: ['列表展示', '条件筛选', '数据维护']
    }
)

const moduleName = computed(() => page.value.module)
const title = computed(() => page.value.title)
const description = computed(() => page.value.description)
const scopes = computed(() => page.value.scopes)
</script>

<style scoped>
.placeholder-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.summary-panel,
.section-panel {
  background: #ffffff;
  border: 1px solid #e6eaf0;
  border-radius: 8px;
}

.summary-panel {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 22px 24px;
}

.eyebrow {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #0284c7;
}

h1 {
  margin: 0 0 8px;
  font-size: 22px;
  color: #101828;
}

p {
  margin: 0;
  color: #667085;
  font-size: 14px;
  line-height: 1.7;
}

.layout-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.section-panel {
  padding: 18px 20px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.panel-head h2 {
  font-size: 16px;
  color: #101828;
}

.panel-head span {
  color: #98a2b3;
  font-size: 13px;
  font-weight: 600;
}

ul {
  display: grid;
  gap: 10px;
  padding-left: 18px;
  color: #475467;
  font-size: 14px;
  line-height: 1.5;
}

@media (max-width: 900px) {
  .layout-grid {
    grid-template-columns: 1fr;
  }
}
</style>
