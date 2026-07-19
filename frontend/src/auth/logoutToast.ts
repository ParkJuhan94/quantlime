// 명시적 로그아웃과 세션 만료로 인한 강제 로그아웃(api/client.ts의
// redirectToLogin) 둘 다 "/"로 리다이렉트된 뒤 토스트를 보여줘야 하는데,
// 강제 로그아웃은 React 상태 밖에서 window.location.assign으로 풀
// 페이지 이동을 하므로 React state로 넘길 수 없다 - sessionStorage로
// 플래그만 넘기고 App.tsx가 마운트 시점에 소비한다.
const LOGOUT_TOAST_KEY = 'ql_show_logout_toast'

function requestLogoutToast(): void {
  sessionStorage.setItem(LOGOUT_TOAST_KEY, '1')
}

function consumeLogoutToast(): boolean {
  const shouldShow = sessionStorage.getItem(LOGOUT_TOAST_KEY) === '1'
  if (shouldShow) {
    sessionStorage.removeItem(LOGOUT_TOAST_KEY)
  }
  return shouldShow
}

export const logoutToast = {
  requestLogoutToast,
  consumeLogoutToast,
}
