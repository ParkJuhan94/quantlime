import { useEffect, useRef, useState } from 'react'

// 실시간 시세 값이 바뀔 때 잠깐 배경색을 칠했다가 옅어지는 연출에 쓴다.
// 값이 null→null이거나 처음 마운트될 때(prev가 없을 때)는 깜빡이지 않는다.
export function useFlashOnChange(value: number | null | undefined): boolean {
  const [flashing, setFlashing] = useState(false)
  const prevValueRef = useRef(value)

  useEffect(() => {
    const prev = prevValueRef.current
    prevValueRef.current = value
    if (prev == null || value == null || prev === value) return

    setFlashing(true)
    const timer = setTimeout(() => setFlashing(false), 1500)
    return () => clearTimeout(timer)
  }, [value])

  return flashing
}
