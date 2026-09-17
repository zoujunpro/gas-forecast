import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { collectMenuPaths, getProfile, getToken, hasProfile, refreshProfile } from '@/utils/auth'
import type { AuthMenu, AuthProfile } from '@/utils/auth'

const viewModules = import.meta.glob('@/views/**/*.vue')
const componentLoaders = Object.fromEntries(
  Object.entries(viewModules).map(([path, loader]) => [path.replace(/^\/src\//, ''), loader])
) as Record<string, RouteRecordRaw['component']>

const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue')
  },
  {
    path: '/',
    name: 'Home',
    meta: { title: '工作台首页' },
    component: componentLoaders['views/dashboard/Home.vue']
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes
})

const registeredDynamicPaths = new Set<string>()

const routeName = (menu: AuthMenu, componentName: string) => `menu-${componentName}-${menu.id}`

const routeMeta = (path: string) => {
  const agentMatch = path.match(/^\/agent\/([^/]+)$/)
  if (agentMatch) {
    return { agentId: agentMatch[1] }
  }

  const configMatch = path.match(/^\/config\/(train|forecast)\/([^/]+)$/)
  if (configMatch) {
    return {
      configType: configMatch[1],
      agentId: configMatch[2]
    }
  }

  return {}
}

const visitMenus = (menus: AuthMenu[] = [], visitor: (menu: AuthMenu) => void) => {
  menus.forEach((menu) => {
    visitor(menu)
    if (menu.children?.length) {
      visitMenus(menu.children, visitor)
    }
  })
}

const installDynamicRoutes = (profile: AuthProfile) => {
  visitMenus(profile.menus, (menu) => {
    const componentName = menu.component || ''
    if (!menu.path || !componentName || registeredDynamicPaths.has(menu.path)) {
      return
    }
    const component = componentLoaders[componentName]
    if (!component) {
      return
    }
    router.addRoute({
      path: menu.path,
      name: routeName(menu, componentName),
      component,
      meta: {
        menuId: menu.id,
        title: menu.name,
        component: componentName,
        ...routeMeta(menu.path)
      }
    })
    registeredDynamicPaths.add(menu.path)
  })
}

router.beforeEach(async (to) => {
  const token = getToken()
  if (to.path !== '/login' && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && token) {
    return '/'
  }

  let profile = getProfile()
  if (token && !hasProfile()) {
    profile = await refreshProfile()
    if (!getToken()) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  if (token) {
    installDynamicRoutes(profile)
  }

  if (to.path !== '/login' && to.path !== '/') {
    let allowedPaths = collectMenuPaths(profile.menus)
    if (!allowedPaths.includes(to.path)) {
      profile = await refreshProfile()
      installDynamicRoutes(profile)
      allowedPaths = collectMenuPaths(profile.menus)
    }
    if (!allowedPaths.includes(to.path)) {
      return '/'
    }
    if (!to.matched.length) {
      return to.fullPath
    }
  }
  return true
})

export default router
