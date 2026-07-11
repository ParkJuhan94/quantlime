import { useState } from 'react'

interface StockLogoProps {
  logoUrl: string
  stockName: string
  className?: string
}

// 네이버 금융의 비공식 정적 로고 경로를 사용하므로(공식 API 아님) 종목별로
// 이미지가 없거나 경로가 바뀔 수 있다 - 로드 실패 시 종목명 첫 글자
// placeholder로 조용히 대체한다.
export function StockLogo({ logoUrl, stockName, className = 'h-8 w-8' }: StockLogoProps) {
  const [failed, setFailed] = useState(false)

  if (failed) {
    return (
      <div
        className={`flex items-center justify-center rounded-full bg-gray-100 text-xs font-medium text-gray-500 ${className}`}
      >
        {stockName.charAt(0)}
      </div>
    )
  }

  return (
    <img
      src={logoUrl}
      alt={`${stockName} 로고`}
      className={`rounded-full object-contain ${className}`}
      onError={() => setFailed(true)}
    />
  )
}
