// 유튜브 요약/텔레그램 요약 페이지·헤더 네비에서 플랫폼을 시각적으로
// 구분하기 위한 마크(2026-09-10). 외부 아이콘 팩 의존성을 추가하지 않고
// 각 플랫폼의 공식 브랜드 컬러만 입힌 최소 형태로 직접 그린다.
interface PlatformLogoProps {
  platform: 'youtube' | 'telegram'
  className?: string
}

export function PlatformLogo({ platform, className = 'h-5 w-5' }: PlatformLogoProps) {
  if (platform === 'youtube') {
    return (
      <svg viewBox="0 0 28 20" className={className} aria-hidden="true">
        <path
          d="M27.4 3.1c-.3-1.2-1.3-2.1-2.4-2.4C22.9.1 14 .1 14 .1s-8.9 0-11 .6c-1.2.3-2.1 1.2-2.4 2.4C.1 5.2.1 10 .1 10s0 4.8.6 6.9c.3 1.2 1.3 2.1 2.4 2.4C5.1 19.9 14 19.9 14 19.9s8.9 0 11-.6c1.2-.3 2.1-1.2 2.4-2.4.6-2.1.6-6.9.6-6.9s0-4.8-.6-6.9z"
          fill="#FF0000"
        />
        <path d="M11.2 14.3 18.5 10l-7.3-4.3z" fill="#fff" />
      </svg>
    )
  }
  return (
    <svg viewBox="0 0 24 24" className={className} aria-hidden="true">
      <circle cx="12" cy="12" r="12" fill="#26A5E4" />
      <path
        d="m5.6 11.9 11.2-4.3c.52-.2.98.13.8.9l-1.9 8.9c-.14.63-.51.78-1.03.49l-2.85-2.1-1.37 1.32c-.15.15-.28.28-.57.28l.2-2.9 5.3-4.79c.23-.2-.05-.32-.35-.11l-6.55 4.13-2.83-.89c-.61-.19-.62-.61.14-.9Z"
        fill="#fff"
      />
    </svg>
  )
}
