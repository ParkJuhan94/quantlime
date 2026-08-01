import { useState } from 'react'

interface StockLogoProps {
  logoUrl: string
  stockName: string
  className?: string
  // 해외(나스닥/뉴욕증권거래소) 종목이면 로고 오른쪽 아래에 성조기 배지를
  // 겹쳐 보여준다(2026-08-01 요청) - 국내와 시각적으로 구분하기 위함.
  overseas?: boolean
}

// 네이버 금융의 비공식 정적 로고 경로를 사용하므로(공식 API 아님) 종목별로
// 이미지가 없거나 경로가 바뀔 수 있다 - 로드 실패 시 종목명 첫 글자
// placeholder로 조용히 대체한다.
export function StockLogo({ logoUrl, stockName, className = 'h-8 w-8', overseas = false }: StockLogoProps) {
  const [failed, setFailed] = useState(false)

  return (
    <div className={`relative inline-block shrink-0 ${className}`}>
      {failed ? (
        <div className="flex h-full w-full items-center justify-center rounded-full bg-gray-100 text-xs font-medium text-gray-500">
          {stockName.charAt(0)}
        </div>
      ) : (
        <img
          src={logoUrl}
          alt={`${stockName} 로고`}
          className="h-full w-full rounded-full object-contain"
          onError={() => setFailed(true)}
        />
      )}
      {overseas && (
        <span
          aria-hidden="true"
          className="absolute bottom-0 right-0 flex h-[45%] w-[45%] translate-x-1/4 translate-y-1/4 items-center justify-center overflow-hidden rounded-full bg-white text-[9px] leading-none ring-1 ring-white"
        >
          🇺🇸
        </span>
      )}
    </div>
  )
}
