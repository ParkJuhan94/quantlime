import { Component, type ErrorInfo, type ReactNode } from 'react'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
}

/**
 * 컴포넌트 렌더 중 발생한 예외를 격리한다 - 이전엔 ErrorBoundary가 전혀
 * 없어 위젯 하나의 렌더 에러가 앱 전체를 백지 화면으로 만들었다
 * (2026-08-17 감사). App.tsx에서 <Routes> 콘텐츠 영역만 감싸고 헤더/
 * 사이드패널은 감싸지 않는다 - 페이지가 깨져도 로그아웃/네비게이션은
 * 계속 동작해야 한다.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('[ErrorBoundary] 렌더링 중 예외 발생', error, errorInfo)
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex flex-col items-center gap-3 py-20 text-center">
          <p className="text-base font-semibold text-gray-900">화면을 불러오는 중 문제가 발생했어요</p>
          <p className="text-sm text-gray-500">잠시 후 다시 시도해주세요</p>
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="rounded-lg bg-gray-900 px-4 py-2 text-sm font-semibold text-white hover:bg-gray-800"
          >
            새로고침
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
