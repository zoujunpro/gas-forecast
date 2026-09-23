const TOKEN_KEY = 'gas_forecast_token'
const PROFILE_KEY = 'gas_forecast_profile'
export const PROFILE_UPDATED_EVENT = 'auth:profile-updated'

export interface AuthMenu {
  id: number
  parentId?: number
  name: string
  path?: string
  component?: string
  icon?: string
  sortNo?: number
  hidden?: number
  children?: AuthMenu[]
}

export interface MenuPathItem {
  id: number
  name: string
  path?: string
}

export interface AuthProfile {
  user?: {
    id: number
    username: string
    realName: string
    avatar?: string
    email?: string
    phone?: string
    orgCode?: string
  }
  roles?: string[]
  permissions?: string[]
  menus?: AuthMenu[]
}

export const getToken = () => localStorage.getItem(TOKEN_KEY) || ''

export const setToken = (token: string) => {
  localStorage.setItem(TOKEN_KEY, token)
}

export const clearToken = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(PROFILE_KEY)
}

export const setProfile = (profile: AuthProfile) => {
  localStorage.setItem(PROFILE_KEY, JSON.stringify(profile))
  window.dispatchEvent(new CustomEvent(PROFILE_UPDATED_EVENT))
}

export const getProfile = (): AuthProfile => {
  const raw = localStorage.getItem(PROFILE_KEY)
  if (!raw) {
    return {}
  }
  try {
    return JSON.parse(raw) as AuthProfile
  } catch {
    return {}
  }
}

export const hasProfile = () => Boolean(localStorage.getItem(PROFILE_KEY))

export const refreshProfile = async (): Promise<AuthProfile> => {
  try {
    const response = await fetch('/auth/permissions')
    const result = await response.json()
    if (!response.ok || result.code !== '0000') {
      clearToken()
      return {}
    }
    const profile: AuthProfile = {
      user: result.data.user,
      roles: result.data.roles,
      permissions: result.data.permissions,
      menus: result.data.menus
    }
    setProfile(profile)
    return profile
  } catch {
    clearToken()
    return {}
  }
}

export const collectMenuPaths = (menus: AuthMenu[] = []): string[] => {
  const paths: string[] = []
  const visit = (items: AuthMenu[]) => {
    items.forEach((item) => {
      if (!item.hidden && item.path) {
        paths.push(item.path)
      }
      if (item.children?.length) {
        visit(item.children)
      }
    })
  }
  visit(menus)
  return paths
}

export const findMenuPath = (menus: AuthMenu[], currentPath: string, parents: MenuPathItem[] = []): MenuPathItem[] => {
  for (const menu of menus) {
    const nextParents = [...parents, { id: menu.id, name: menu.name, path: menu.path }]
    if (menu.path === currentPath) {
      return nextParents
    }
    if (menu.children?.length) {
      const matched = findMenuPath(menu.children, currentPath, nextParents)
      if (matched.length) {
        return matched
      }
    }
  }
  return []
}

export const getCurrentMenuPath = (currentPath: string) => findMenuPath(getProfile().menus || [], currentPath)

export const hasPermission = (permission?: string) => {
  if (!permission) {
    return true
  }
  return Boolean(getProfile().permissions?.includes(permission))
}

export const installAuthFetch = () => {
  const originalFetch = window.fetch.bind(window)
  window.fetch = (input: RequestInfo | URL, init: RequestInit = {}) => {
    const token = getToken()
    const headers = new Headers(init.headers)
    const requestUrl = new URL(input instanceof Request ? input.url : input.toString(), window.location.href)
    if (requestUrl.origin === window.location.origin && token && !headers.has('X-Access-Token')) {
      headers.set('X-Access-Token', token)
    }
    return originalFetch(input, { ...init, headers }).then((response) => {
      if (requestUrl.origin === window.location.origin && response.status === 401) {
        clearToken()
        window.dispatchEvent(new CustomEvent('auth:expired'))
      }
      return response
    })
  }
}
