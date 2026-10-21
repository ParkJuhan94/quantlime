import { useTheme } from '../../hooks/useTheme'

interface ThemeToggleProps {
  // rail: 오른쪽 사이드 레일의 관심/최근 본/의견 버튼과 같은 세로 아이콘+라벨 모양
  // header: 레일이 없는 화면(모바일, 구독/관리자 등)에서 헤더에 두는 아이콘 버튼
  variant: 'rail' | 'header'
}

function MoonIcon({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" strokeLinejoin="round" />
    </svg>
  )
}

function SunIcon({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <circle cx="12" cy="12" r="4" />
      <path
        d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"
        strokeLinecap="round"
      />
    </svg>
  )
}

// 아이콘과 라벨은 "지금 상태"가 아니라 "누르면 바뀔 모드"를 보여준다
// (라이트일 때 달+"다크", 다크일 때 해+"라이트").
export function ThemeToggle({ variant }: ThemeToggleProps) {
  const { theme, toggleTheme } = useTheme()
  const isDark = theme === 'dark'
  const targetLabel = isDark ? '라이트' : '다크'
  const ariaLabel = `${targetLabel} 모드로 전환`

  if (variant === 'header') {
    return (
      <button
        type="button"
        onClick={toggleTheme}
        aria-label={ariaLabel}
        title={ariaLabel}
        className="rounded-full p-2 text-gray-500 transition hover:bg-gray-100"
      >
        {isDark ? <SunIcon size={18} /> : <MoonIcon size={18} />}
      </button>
    )
  }

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-label={ariaLabel}
      title={ariaLabel}
      className="flex flex-col items-center gap-1 rounded-lg px-1 py-1.5 text-[#999] hover:bg-gray-100"
    >
      {isDark ? <SunIcon size={19} /> : <MoonIcon size={19} />}
      <span className="text-[10px] font-semibold text-gray-400">{targetLabel}</span>
    </button>
  )
}
