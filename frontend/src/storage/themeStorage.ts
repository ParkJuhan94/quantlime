export type Theme = 'light' | 'dark'

const KEY = 'ql_theme'

// 사용자가 토글로 직접 고른 값만 저장한다. 저장값이 없으면 시스템 설정
// (prefers-color-scheme)을 따른다. 시크릿 모드 등에서 localStorage가 막혀
// 있어도 화면은 떠야 하므로 읽기/쓰기 모두 실패를 삼킨다.
function read(): Theme | null {
  try {
    const raw = localStorage.getItem(KEY)
    return raw === 'light' || raw === 'dark' ? raw : null
  } catch {
    return null
  }
}

function write(theme: Theme): void {
  try {
    localStorage.setItem(KEY, theme)
  } catch {
    // 저장에 실패해도 이번 방문 동안은 화면에 적용된다.
  }
}

export const themeStorage = {
  key: KEY,
  read,
  write,
}
