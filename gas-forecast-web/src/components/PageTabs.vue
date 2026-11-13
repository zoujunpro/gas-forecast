<template>
  <nav class="page-tabs" aria-label="打开的页面">
    <div ref="tabsScrollRef" class="tabs-scroll" @wheel="handleTabsWheel">
      <button
        v-for="tab in tabs"
        :key="tab.path"
        type="button"
        :class="['tab-item', { active: tab.path === route.path }]"
        @click="openTab(tab.path)"
        @contextmenu.prevent="openContextMenu($event, tab.path)"
      >
        <span class="tab-title">{{ tab.title }}</span>
        <el-icon v-if="isClosable(tab.path)" class="tab-close" @click.stop="closeTab(tab.path)">
          <Close />
        </el-icon>
      </button>
    </div>

    <div
      v-if="contextMenu.visible"
      class="tab-context-menu"
      :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
      @click.stop
    >
      <button
        type="button"
        class="context-menu-item"
        :disabled="!contextTargetClosable"
        @click="runContextCommand('current')"
      >
        关闭当前
      </button>
      <button type="button" class="context-menu-item" @click="runContextCommand('others')">关闭其他</button>
      <button
        type="button"
        class="context-menu-item"
        :disabled="!hasRightTabsFromContext"
        @click="runContextCommand('right')"
      >
        关闭右侧
      </button>
      <button
        type="button"
        class="context-menu-item"
        :disabled="!hasLeftTabsFromContext"
        @click="runContextCommand('left')"
      >
        关闭左侧
      </button>
      <button
        type="button"
        class="context-menu-item divided"
        :disabled="tabs.every((tab) => !isClosable(tab.path))"
        @click="runContextCommand('all')"
      >
        关闭所有
      </button>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Close } from '@element-plus/icons-vue'

interface PageTab {
  path: string
  title: string
}

type TabCommand = 'current' | 'others' | 'right' | 'left' | 'all'

const homePath = '/'
const route = useRoute()
const router = useRouter()
const tabsScrollRef = ref<HTMLElement | null>(null)

const homeTab = (): PageTab => ({
  path: homePath,
  title: String(router.resolve(homePath).meta.title || '工作台首页')
})

const tabs = ref<PageTab[]>([homeTab()])
const contextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  targetPath: homePath
})

const contextTargetIndex = computed(() => tabs.value.findIndex((tab) => tab.path === contextMenu.targetPath))
const contextTargetClosable = computed(() => isClosable(contextMenu.targetPath))
const hasLeftTabsFromContext = computed(() =>
  tabs.value.some((tab, index) => index < contextTargetIndex.value && isClosable(tab.path))
)
const hasRightTabsFromContext = computed(() =>
  tabs.value.some((tab, index) => index > contextTargetIndex.value && isClosable(tab.path))
)

const isClosable = (path: string) => path !== homePath

const normalizeTabs = () => {
  const currentTabs = tabs.value.filter((tab) => tab.path !== homePath)
  tabs.value = [homeTab(), ...currentTabs]
}

const ensureTab = (path: string) => {
  normalizeTabs()

  const resolved = router.resolve(path)
  const title = String(resolved.meta.title || '功能页面')
  const currentTab = tabs.value.find((tab) => tab.path === path)

  if (currentTab) {
    currentTab.title = title
    return
  }

  tabs.value.push({ path, title })
}

const scrollActiveTabIntoView = () => {
  void nextTick(() => {
    const activeTab = tabsScrollRef.value?.querySelector<HTMLElement>('.tab-item.active')
    activeTab?.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' })
  })
}

const handleTabsWheel = (event: WheelEvent) => {
  const scrollContainer = tabsScrollRef.value
  if (!scrollContainer || scrollContainer.scrollWidth <= scrollContainer.clientWidth) {
    return
  }

  if (Math.abs(event.deltaY) > Math.abs(event.deltaX)) {
    event.preventDefault()
    scrollContainer.scrollLeft += event.deltaY
  }
}

watch(
  () => route.path,
  (path) => {
    ensureTab(path)
    scrollActiveTabIntoView()
  },
  { immediate: true }
)

const openTab = (path: string) => {
  closeContextMenu()
  if (path !== route.path) {
    void router.push(path)
  }
}

const navigateAfterClosing = (closedPath: string, closedIndex: number) => {
  if (closedPath !== route.path) {
    return
  }

  const nextTab =
    tabs.value[closedIndex] ?? tabs.value[closedIndex - 1] ?? tabs.value.find((tab) => tab.path === homePath)
  void router.push(nextTab.path)
}

const navigateAfterBulkClosing = (fallbackPath = homePath) => {
  if (tabs.value.some((tab) => tab.path === route.path)) {
    return
  }

  const fallbackTab =
    tabs.value.find((tab) => tab.path === fallbackPath) ?? tabs.value.find((tab) => tab.path === homePath)
  if (fallbackTab) {
    void router.push(fallbackTab.path)
  }
}

const closeTab = (path: string) => {
  if (!isClosable(path)) {
    return
  }

  const index = tabs.value.findIndex((tab) => tab.path === path)
  if (index < 0) {
    return
  }

  tabs.value.splice(index, 1)
  navigateAfterClosing(path, index)
}

const closeOthers = (targetPath = route.path) => {
  tabs.value = tabs.value.filter((tab) => tab.path === homePath || tab.path === targetPath)
  normalizeTabs()
  navigateAfterBulkClosing(targetPath)
}

const closeAll = () => {
  tabs.value = [homeTab()]
  if (route.path !== homePath) {
    void router.push(homePath)
  }
}

const closeLeft = (targetPath = route.path) => {
  const index = tabs.value.findIndex((tab) => tab.path === targetPath)
  if (index <= 0) {
    return
  }

  tabs.value = tabs.value.filter((tab, tabIndex) => tab.path === homePath || tabIndex >= index)
  normalizeTabs()
  navigateAfterBulkClosing(targetPath)
}

const closeRight = (targetPath = route.path) => {
  const index = tabs.value.findIndex((tab) => tab.path === targetPath)
  if (index < 0) {
    return
  }

  tabs.value = tabs.value.filter((tab, tabIndex) => tab.path === homePath || tabIndex <= index)
  normalizeTabs()
  navigateAfterBulkClosing(targetPath)
}

const closeContextMenu = () => {
  contextMenu.visible = false
}

const openContextMenu = (event: MouseEvent, path: string) => {
  contextMenu.targetPath = path
  contextMenu.x = Math.min(event.clientX, window.innerWidth - 132)
  contextMenu.y = Math.min(event.clientY, window.innerHeight - 186)
  contextMenu.visible = true
}

const runContextCommand = (command: TabCommand) => {
  const targetPath = contextMenu.targetPath
  closeContextMenu()

  switch (command) {
    case 'current':
      closeTab(targetPath)
      break
    case 'others':
      closeOthers(targetPath)
      break
    case 'right':
      closeRight(targetPath)
      break
    case 'left':
      closeLeft(targetPath)
      break
    case 'all':
      closeAll()
      break
  }
}

onMounted(() => {
  window.addEventListener('click', closeContextMenu)
  window.addEventListener('scroll', closeContextMenu, true)
})

onBeforeUnmount(() => {
  window.removeEventListener('click', closeContextMenu)
  window.removeEventListener('scroll', closeContextMenu, true)
})
</script>

<style scoped>
.page-tabs {
  width: 100%;
  min-width: 0;
  height: 44px;
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 7px;
  padding: 0 12px;
  overflow: hidden;
  background: rgba(255, 255, 255, .96);
  border-bottom: 1px solid var(--app-border);
}

.tabs-scroll {
  min-width: 0;
  height: 100%;
  flex: 1 1 auto;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 0;
  overflow-x: auto;
  overflow-y: hidden;
  overscroll-behavior-x: contain;
  scroll-padding: 0 6px;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.tabs-scroll::-webkit-scrollbar {
  display: none;
}

.tab-item {
  height: 28px;
  max-width: 168px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  min-width: 0;
  padding: 0 10px;
  border: 1px solid var(--app-border);
  border-radius: 7px;
  background: #f8fafc;
  color: var(--app-text-secondary);
  font-family: inherit;
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
  transition:
    color 0.2s,
    border-color 0.2s,
    background 0.2s;
}

.tab-item.active {
  color: var(--app-primary-hover);
  border-color: #bfdbfe;
  background: var(--app-primary-soft);
  font-weight: 600;
}

.tab-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tab-close {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  border-radius: 50%;
  color: var(--app-text-muted);
}

.tab-close:hover {
  color: var(--app-text);
  background: rgba(15, 23, 42, 0.08);
}

.tab-context-menu {
  position: fixed;
  z-index: 3000;
  width: 120px;
  padding: 4px;
  border: 1px solid var(--app-border);
  border-radius: 6px;
  background: var(--app-surface);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.14);
}

.context-menu-item {
  width: 100%;
  height: 30px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--app-text);
  font-family: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.context-menu-item:hover:not(:disabled) {
  color: var(--app-primary-hover);
  background: var(--app-primary-soft);
}

.context-menu-item:disabled {
  color: var(--app-text-disabled);
  cursor: not-allowed;
}

.context-menu-item.divided {
  margin-top: 4px;
  border-top: 1px solid var(--app-border-soft);
  border-radius: 0 0 4px 4px;
}
</style>
