// 차트 축/크로스헤어/범례가 공유하는 가격 표시 규칙. 국내(KRW)는 원 단위 정수, 미국 주식·지수·환율은 소수점
// 2자리다 - 정수로 고정하면 해외 종목과 지수의 소수점이 축 라벨과 크로스헤어에서 잘렸다.
export function pricePrecisionFor(currency: 'KRW' | 'USD'): number {
  return currency === 'USD' ? 2 : 0
}

// 축/크로스헤어/마지막 가격 라벨에 천 단위 구분과 고정 소수 자리를 적용하는 시리즈 priceFormat - 기본 'price' 형식은
// 구분 기호가 없어 비트코인 같은 큰 값이 "130000000"으로 읽기 어려웠다.
export function priceFormatOptions(precision: number): {
  type: 'custom'
  minMove: number
  formatter: (price: number) => string
} {
  return {
    type: 'custom',
    minMove: 1 / 10 ** precision,
    formatter: (price: number) => formatChartPrice(price, precision),
  }
}

export function formatChartPrice(value: number, precision: number): string {
  return value.toLocaleString('ko-KR', { minimumFractionDigits: precision, maximumFractionDigits: precision })
}

// 주식 거래량은 정수 주(株)라 반올림하고, 코인 거래량은 1 미만의 소수가 의미 있어 소수 4자리까지 보여준다.
export function formatChartVolume(volume: number): string {
  if (volume >= 1000) return Math.round(volume).toLocaleString('ko-KR')
  return volume.toLocaleString('ko-KR', { maximumFractionDigits: 4 })
}

// 전봉 종가 대비 등락률(%). 전봉이 없거나 0이면 계산할 수 없어 null.
export function changeRateFromPrevious(close: number, prevClose: number | null | undefined): number | null {
  if (prevClose == null || prevClose === 0) return null
  return ((close - prevClose) / prevClose) * 100
}

// epoch 초(UTC)를 한국 시각으로 표시한다. KST는 서머타임이 없어 고정 오프셋(+9h)으로 충분하다 -
// lightweight-charts는 epoch 초를 UTC로 그리기 때문에 축/크로스헤어 라벨은 직접 만들어야 한다.
const KST_OFFSET_SECONDS = 9 * 60 * 60

function kstParts(epochSeconds: number) {
  const date = new Date((epochSeconds + KST_OFFSET_SECONDS) * 1000)
  return {
    year: date.getUTCFullYear(),
    month: date.getUTCMonth() + 1,
    day: date.getUTCDate(),
    hour: String(date.getUTCHours()).padStart(2, '0'),
    minute: String(date.getUTCMinutes()).padStart(2, '0'),
  }
}

export function formatKstTime(epochSeconds: number): string {
  const { hour, minute } = kstParts(epochSeconds)
  return `${hour}:${minute}`
}

export function formatKstMonthDay(epochSeconds: number): string {
  const { month, day } = kstParts(epochSeconds)
  return `${month}/${day}`
}

export function formatKstDate(epochSeconds: number): string {
  const { year, month, day } = kstParts(epochSeconds)
  return `${year}.${String(month).padStart(2, '0')}.${String(day).padStart(2, '0')}`
}

export function formatKstDateTime(epochSeconds: number): string {
  return `${formatKstDate(epochSeconds)} ${formatKstTime(epochSeconds)}`
}

// 'YYYY-MM-DD'를 'YYYY.MM.DD'로 - 일봉 범례/크로스헤어 라벨용.
export function formatTradeDate(tradeDate: string): string {
  return tradeDate.replaceAll('-', '.')
}

// KST 날짜('YYYY-MM-DD') - 업비트 일/주/월봉(epoch 초)을 일봉 차트용 영업일 문자열로 바꿀 때 쓴다.
export function kstDateString(epochSeconds: number): string {
  const { year, month, day } = kstParts(epochSeconds)
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}
