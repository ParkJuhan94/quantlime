import { Link } from 'react-router-dom'
import type { ScoreRankingResponse } from '../../types/score'
import { GradeBadge } from './GradeBadge'
import { formatScore } from '../../utils/scoreFormat'

interface ScoreRankingTableProps {
  rankings: ScoreRankingResponse[]
}

export function ScoreRankingTable({ rankings }: ScoreRankingTableProps) {
  return (
    // 좁은 화면(모바일)에서 6개 컬럼이 다 안 들어가면 테이블만 가로
    // 스크롤되게 하고, 페이지 전체가 밀리지 않도록 한다.
    <div className="overflow-x-auto">
      <table className="w-full min-w-[420px] text-left">
        <thead>
          <tr className="border-b border-gray-200 text-xs text-gray-500">
            <th className="pb-2 font-medium">순위</th>
            <th className="pb-2 font-medium">종목</th>
            <th className="pb-2 text-right font-medium">종합</th>
            <th className="pb-2 text-right font-medium">추세추종</th>
            <th className="pb-2 text-right font-medium">평균회귀</th>
            <th className="pb-2 font-medium">등급</th>
          </tr>
        </thead>
        <tbody>
          {rankings.map((item, index) => (
            <tr key={item.stockCode} className="border-b border-gray-100">
              <td className="py-2 text-sm text-gray-500">{index + 1}</td>
              <td className="py-2">
                <Link to={`/stocks/${item.stockCode}`} className="font-medium text-gray-900 hover:underline">
                  {item.stockName}
                </Link>
                <span className="ml-2 text-xs text-gray-400">{item.stockCode}</span>
                {item.insufficientData && (
                  <span className="ml-2 rounded bg-gray-100 px-1.5 py-0.5 text-[10px] text-gray-500">
                    데이터 부족
                  </span>
                )}
              </td>
              <td className="py-2 text-right text-sm font-semibold text-gray-900">
                {formatScore(item.compositeScore)}
              </td>
              <td className="py-2 text-right text-sm text-gray-600">{formatScore(item.trendScore)}</td>
              <td className="py-2 text-right text-sm text-gray-600">{formatScore(item.meanReversionScore)}</td>
              <td className="py-2">
                <GradeBadge grade={item.grade} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
