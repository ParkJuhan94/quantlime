import type { BucketResponse, HorizonBacktestResponse } from '../../types/backtest'

// 표본이 너무 적은 버킷(N<10)은 통계적으로 신뢰하기 어렵다는 걸 시각적으로
// 옅게 표시한다(백테스트 방법론 - 버킷 N 표기 원칙 참고).
const MIN_RELIABLE_SAMPLE_SIZE = 10
const BUCKET_NUMBERS = [1, 2, 3, 4, 5]

export function QuantileBucketTable({ horizons }: { horizons: HorizonBacktestResponse[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[480px] text-xs">
        <thead>
          <tr className="border-b border-gray-100 text-gray-400">
            <th className="py-1.5 text-left font-medium">버킷</th>
            {horizons.map((h) => (
              <th key={h.horizonDays} className="py-1.5 text-right font-medium">
                {h.horizonDays}일
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {BUCKET_NUMBERS.map((bucketNumber) => (
            <tr key={bucketNumber} className="border-b border-gray-50">
              <td className="py-1.5 text-gray-500">
                {bucketNumber}
                {bucketNumber === 1 && <span className="ml-1 text-[10px] text-gray-400">(최저점)</span>}
                {bucketNumber === 5 && <span className="ml-1 text-[10px] text-gray-400">(최고점)</span>}
              </td>
              {horizons.map((h) => {
                const bucket = h.buckets.find((b) => b.bucketNumber === bucketNumber)
                return (
                  <td key={h.horizonDays} className="py-1.5 text-right">
                    {bucket ? <BucketCell bucket={bucket} /> : <span className="text-gray-300">-</span>}
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function BucketCell({ bucket }: { bucket: BucketResponse }) {
  const isLowSample = bucket.sampleSize < MIN_RELIABLE_SAMPLE_SIZE
  return (
    <div className={isLowSample ? 'text-gray-300' : 'text-gray-900'}>
      <p className="font-semibold">
        {bucket.meanExcessReturn != null ? `${(bucket.meanExcessReturn * 100).toFixed(2)}%` : '-'}
      </p>
      <p className="text-[10px] text-gray-400">
        승률 {bucket.hitRate != null ? `${(bucket.hitRate * 100).toFixed(0)}%` : '-'} · N={bucket.sampleSize}
      </p>
    </div>
  )
}
