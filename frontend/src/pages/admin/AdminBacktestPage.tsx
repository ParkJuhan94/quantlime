import { useState } from 'react'
import { prepareBacktestDataset, runBacktest, runCrossSectionalBacktest, runUniverseBacktest } from '../../api/admin'
import { AdminActionCard } from '../../components/admin/AdminActionCard'
import type { BacktestMarket } from '../../types/admin'

const MARKETS: BacktestMarket[] = ['KOSPI', 'KOSDAQ', 'NASDAQ', 'NYSE']
const INPUT_CLASS =
  'w-full rounded-lg border border-gray-200 px-3 py-2 text-sm text-gray-900 outline-none focus:border-gray-900'

function Checkbox({
  id,
  checked,
  onChange,
  label,
}: {
  id: string
  checked: boolean
  onChange: (v: boolean) => void
  label: string
}) {
  return (
    <label htmlFor={id} className="flex items-center gap-2 text-sm text-gray-700">
      <input id={id} type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} />
      {label}
    </label>
  )
}

export function AdminBacktestPage() {
  const [stockCode, setStockCode] = useState('')
  const [force, setForce] = useState(false)
  const [market, setMarket] = useState<BacktestMarket | ''>('')
  const [scoreVersion, setScoreVersion] = useState('v3.0')
  const [nullTest, setNullTest] = useState(false)
  const [nullRepeats, setNullRepeats] = useState(200)

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-base font-bold text-gray-900">백테스트</h2>
        <p className="text-sm text-gray-500">
          무거운 연산이라 응답이 길어지면 연결이 끊길 수 있어요. 끊겨도 서버에서는 계속 진행되니, 완료 여부는 서버
          로그나 저장된 결과 건수로 확인하세요.
        </p>
      </div>

      <AdminActionCard
        title="1. 백테스트용 데이터 준비"
        description="국내와 해외 거래대금 상위 500개 유니버스를 정하고 백필하며, 벤치마크 지수(KOSPI, KOSDAQ, NASDAQ, S&P500)도 한 번에 채웁니다."
        buttonLabel="데이터 준비"
        confirm={{
          title: '백테스트용 데이터를 준비할까요?',
          description: '유니버스 선정과 백필을 한 번에 돌려서 오래 걸리고 외부 시세 호출을 많이 써요.',
        }}
        run={prepareBacktestDataset}
      />

      <AdminActionCard
        title="2. 종목 백테스트"
        description="종목 하나의 백테스트를 실행합니다. 국내(KOSPI, KOSDAQ)와 해외(NASDAQ, NYSE)를 지원해요."
        buttonLabel="종목 실행"
        disabled={!stockCode.trim()}
        run={() => runBacktest(stockCode.trim())}
      >
        <label className="block text-sm font-semibold text-gray-700" htmlFor="backtest-stock-code">
          종목 코드
          <input
            id="backtest-stock-code"
            value={stockCode}
            onChange={(event) => setStockCode(event.target.value)}
            placeholder="005930"
            className={`mt-1 ${INPUT_CLASS}`}
          />
        </label>
      </AdminActionCard>

      <AdminActionCard
        title="3. 유니버스 전체 백테스트"
        description="국내와 해외 상위 500개 종목 전체를 순회해 실행합니다. 오늘 이미 실행한 종목은 건너뛰어요. 수십 분 이상 걸릴 수 있어요."
        buttonLabel="전체 실행"
        confirm={{
          title: '유니버스 전체 백테스트를 실행할까요?',
          description: force
            ? '다시 실행 옵션이 켜져 있어 오늘 실행한 종목도 전부 다시 돌려요. 수십 분 이상 걸릴 수 있어요.'
            : '수십 분 이상 걸릴 수 있고, 오늘 이미 실행한 종목은 건너뛰어요.',
        }}
        run={() => runUniverseBacktest(force)}
      >
        <Checkbox id="backtest-force" checked={force} onChange={setForce} label="오늘 실행한 종목도 다시 실행" />
      </AdminActionCard>

      <AdminActionCard
        title="4. 횡단면 Rank IC 백테스트"
        description="같은 날짜의 여러 종목을 줄 세워 비교합니다. 3번(유니버스 전체)이 먼저 끝나 있어야 해요. 여러 시장을 동시에 여러 요청으로 나눠 실행하지 마세요. 한쪽이 느려지면 나머지가 전부 실패해요."
        buttonLabel="횡단면 실행"
        disabled={!scoreVersion.trim() || nullRepeats < 1}
        confirm={{
          title: '횡단면 백테스트를 실행할까요?',
          description: nullTest
            ? `순환이동 널 분포까지 ${nullRepeats}회 계산해서 훨씬 오래 걸려요.`
            : '저장된 결과를 줄 세워 계산해요. 시장을 비우면 4개 시장 전부 실행해요.',
        }}
        run={() =>
          runCrossSectionalBacktest({
            market: market || undefined,
            scoreVersion: scoreVersion.trim(),
            nullTest,
            nullRepeats,
          })
        }
      >
        <label className="block text-sm font-semibold text-gray-700" htmlFor="backtest-market">
          시장
          <select
            id="backtest-market"
            value={market}
            onChange={(event) => setMarket(event.target.value as BacktestMarket | '')}
            className={`mt-1 ${INPUT_CLASS}`}
          >
            <option value="">전체(4개 시장)</option>
            {MARKETS.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-sm font-semibold text-gray-700" htmlFor="backtest-score-version">
          스코어 버전
          <input
            id="backtest-score-version"
            value={scoreVersion}
            onChange={(event) => setScoreVersion(event.target.value)}
            placeholder="v3.0"
            className={`mt-1 ${INPUT_CLASS}`}
          />
        </label>
        <Checkbox
          id="backtest-null-test"
          checked={nullTest}
          onChange={setNullTest}
          label="널 분포 검정 포함(오래 걸림)"
        />
        {nullTest && (
          <label className="block text-sm font-semibold text-gray-700" htmlFor="backtest-null-repeats">
            널 분포 반복 횟수
            <input
              id="backtest-null-repeats"
              type="number"
              min={1}
              value={nullRepeats}
              onChange={(event) => setNullRepeats(Number(event.target.value))}
              className={`mt-1 ${INPUT_CLASS}`}
            />
          </label>
        )}
      </AdminActionCard>
    </div>
  )
}
