import { useCallback, useSyncExternalStore } from 'react'
import { themeStorage, type Theme } from '../storage/themeStorage'

// 현재 테마의 단일 출처는 <html data-theme>다. index.html의 인라인 스크립트가
// React가 뜨기 전에 먼저 채워 두므로(깜빡임 방지), 여기서는 그 값을 읽고
// 바뀌면 구독자에게 알리기만 한다. 차트처럼 CSS 변수를 못 쓰는 곳도 이 훅으로
// 같은 값을 받는다.
const listeners = new Set<() => void>()

function currentTheme(): Theme {
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'
}

function applyTheme(theme: Theme) {
  if (theme === 'dark') {
    document.documentElement.dataset.theme = 'dark'
  } else {
    delete document.documentElement.dataset.theme
  }
  listeners.forEach((listener) => listener())
}

// 사용자가 아직 직접 고르지 않았다면 OS 설정이 바뀔 때 같이 따라간다.
function handleSystemChange(event: MediaQueryListEvent) {
  if (themeStorage.read() === null) {
    applyTheme(event.matches ? 'dark' : 'light')
  }
}

function subscribe(listener: () => void) {
  const systemQuery = window.matchMedia('(prefers-color-scheme: dark)')
  if (listeners.size === 0) {
    systemQuery.addEventListener('change', handleSystemChange)
  }
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
    if (listeners.size === 0) {
      systemQuery.removeEventListener('change', handleSystemChange)
    }
  }
}

export function useTheme() {
  const theme = useSyncExternalStore(subscribe, currentTheme, () => 'light' as Theme)

  const toggleTheme = useCallback(() => {
    const next: Theme = currentTheme() === 'dark' ? 'light' : 'dark'
    themeStorage.write(next)
    applyTheme(next)
  }, [])

  return { theme, toggleTheme }
}
