import { useState } from 'react'
import {
  backfillRegularClose,
  rebuildScores,
  refreshPricesAndScores,
  repairAdjustedPrices,
  resettlePrices,
  syncDomesticStockMaster,
  syncOverseasStockMaster,
} from '../../api/admin'
import { AdminActionCard } from '../../components/admin/AdminActionCard'
import { BatchStartedResult } from '../../components/admin/BatchStartedResult'
import type { PriceRepairMarket } from '../../types/admin'

const INPUT_CLASS =
  'w-full rounded-lg border border-gray-200 px-3 py-2 text-sm text-gray-900 outline-none focus:border-gray-900'

const MARKET_OPTIONS: { value: PriceRepairMarket; label: string }[] = [
  { value: 'all', label: '국내와 해외 모두' },
  { value: 'domestic', label: '국내만' },
  { value: 'overseas', label: '해외만' },
]

function splitCodes(raw: string): string[] {
  return raw
    .split(/[\s,]+/)
    .map((code) => code.trim())
    .filter(Boolean)
}

export function AdminDataOpsPage() {
  const [refreshCode, setRefreshCode] = useState('')
  const [resettleMarket, setResettleMarket] = useState<PriceRepairMarket>('all')
  const [resettleFrom, setResettleFrom] = useState('')
  const [repairFrom, setRepairFrom] = useState('')
  const [dryRun, setDryRun] = useState(true)
  const [backfillCodes, setBackfillCodes] = useState('')
  const [backfillDays, setBackfillDays] = useState('')
  const [rebuildFrom, setRebuildFrom] = useState('')

  const days = backfillDays.trim() === '' ? undefined : Number(backfillDays)
  const backfillTargets = splitCodes(backfillCodes)

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-base font-bold text-gray-900">데이터 복구</h2>
        <p className="text-sm text-gray-500">
          시세와 스코어가 어긋났을 때 운영에서 직접 바로잡는 도구예요. 오래 걸리는 작업은 응답이 끊겨도 서버에서는 계속
          진행되니, 완료 여부는 서버 로그로 확인하세요.
        </p>
      </div>

      <AdminActionCard
        title="1. 국내 종목 마스터 동기화"
        description="신규 상장과 상장 폐지를 반영합니다."
        buttonLabel="국내 동기화"
        run={syncDomesticStockMaster}
      />

      <AdminActionCard
        title="2. 해외 종목 마스터 동기화"
        description="NASDAQ과 NYSE 종목을 동기화합니다."
        buttonLabel="해외 동기화"
        run={syncOverseasStockMaster}
      />

      <AdminActionCard
        title="3. 가격과 스코어 채우기"
        description="마지막 저장일 다음 날부터 오늘까지 비어 있는 가격과 스코어를 채웁니다. 종목 코드를 비우면 전종목(수 분에서 수십 분)을 처리하고, 이미 다른 갱신이 돌고 있으면 건너뜁니다."
        buttonLabel="채우기 실행"
        confirm={
          refreshCode.trim()
            ? undefined
            : {
                title: '전종목을 채울까요?',
                description: '국내와 해외 약 9천 종목이라 수 분에서 수십 분이 걸리고 외부 시세 호출을 많이 써요.',
              }
        }
        run={() => refreshPricesAndScores(refreshCode.trim() || undefined)}
      >
        <label className="block text-sm font-semibold text-gray-700" htmlFor="data-refresh-code">
          종목 코드(선택)
          <input
            id="data-refresh-code"
            value={refreshCode}
            onChange={(event) => setRefreshCode(event.target.value)}
            placeholder="비우면 전종목"
            className={`mt-1 ${INPUT_CLASS}`}
          />
        </label>
      </AdminActionCard>

      <AdminActionCard
        title="4. 가격 재확정"
        description="선택한 시작일부터 오늘까지 전 상장 종목의 가격을 다시 조회해 덮어씁니다. 장중 값이 그대로 굳은 기존 오염을 고칠 때 써요."
        buttonLabel="재확정 실행"
        disabled={!resettleFrom}
        confirm={{
          title: '가격을 다시 조회해 덮어쓸까요?',
          description: `${resettleFrom}부터 오늘까지 전 상장 종목을 다시 조회해요. 오래 걸리고 외부 시세 호출을 많이 써요.`,
        }}
        run={() => resettlePrices(resettleMarket, resettleFrom)}
      >
        <label className="block text-sm font-semibold text-gray-700" htmlFor="data-resettle-market">
          시장
          <select
            id="data-resettle-market"
            value={resettleMarket}
            onChange={(event) => setResettleMarket(event.target.value as PriceRepairMarket)}
            className={`mt-1 ${INPUT_CLASS}`}
          >
            {MARKET_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-sm font-semibold text-gray-700" htmlFor="data-resettle-from">
          시작일
          <input
            id="data-resettle-from"
            type="date"
            value={resettleFrom}
            onChange={(event) => setResettleFrom(event.target.value)}
            className={`mt-1 ${INPUT_CLASS}`}
          />
        </label>
      </AdminActionCard>

      <AdminActionCard
        title="5. 수정주가 점검과 복구"
        description="액면 분할이나 병합으로 종가가 가격제한폭을 넘어 튄 종목을 찾습니다. 점검만 하는 모드가 기본이고, 끄면 걸린 종목을 전 구간 다시 채웁니다."
        buttonLabel={dryRun ? '점검 실행' : '복구 실행'}
        disabled={!repairFrom}
        tone={dryRun ? 'default' : 'danger'}
        confirm={
          dryRun
            ? undefined
            : {
                title: '걸린 종목을 전 구간 다시 채울까요?',
                description: '점검 모드를 껐어요. 가격 제한폭을 넘은 종목의 과거 가격을 전부 다시 받아 덮어써요.',
              }
        }
        run={() => repairAdjustedPrices(repairFrom, dryRun)}
        renderResult={(jumps) =>
          jumps.length === 0 ? (
            <p>튄 종목이 없어요.</p>
          ) : (
            <ul className="flex flex-col gap-1">
              <li className="text-xs text-gray-400">{jumps.length}건 발견</li>
              {jumps.map((jump) => (
                <li key={`${jump.stockCode}-${jump.tradeDate}`}>
                  {jump.stockCode} · {jump.tradeDate} · {jump.previousClose.toLocaleString()} →{' '}
                  {jump.close.toLocaleString()} (×{jump.ratio.toFixed(2)})
                </li>
              ))}
            </ul>
          )
        }
      >
        <label className="block text-sm font-semibold text-gray-700" htmlFor="data-repair-from">
          시작일
          <input
            id="data-repair-from"
            type="date"
            value={repairFrom}
            onChange={(event) => setRepairFrom(event.target.value)}
            className={`mt-1 ${INPUT_CLASS}`}
          />
        </label>
        <label htmlFor="data-repair-dry-run" className="flex items-center gap-2 text-sm text-gray-700">
          <input
            id="data-repair-dry-run"
            type="checkbox"
            checked={dryRun}
            onChange={(event) => setDryRun(event.target.checked)}
          />
          점검만 하기(복구하지 않음)
        </label>
      </AdminActionCard>

      <AdminActionCard
        title="6. 정규장 종가 소급 확정"
        description="종가를 정규장 마감가로 바꾸기 전에 저장된 행을 1분봉으로 다시 확정합니다. 이미 확정된 행은 건너뛰어 다시 실행해도 안전해요. 전종목은 약 1.5시간이 걸리니 먼저 종목 몇 개로 확인하세요."
        buttonLabel="소급 확정"
        disabled={days !== undefined && (!Number.isInteger(days) || days < 1)}
        confirm={
          backfillTargets.length > 0
            ? undefined
            : {
                title: '국내 전종목을 소급 확정할까요?',
                description:
                  '약 1.5시간이 걸려요. 서버를 막 켠 직후라면 따라잡기 작업이 끝난 뒤에 실행하고, 끝나면 한 번 더 실행하세요.',
              }
        }
        run={() => backfillRegularClose(backfillTargets, days)}
        renderResult={(result) => (
          <p>
            확정 {result.confirmed.toLocaleString()}건 · 건너뜀 {result.skipped.toLocaleString()}건 · 실패{' '}
            {result.failed.toLocaleString()}건
          </p>
        )}
      >
        <label className="block text-sm font-semibold text-gray-700" htmlFor="data-backfill-codes">
          종목 코드(선택, 쉼표나 공백으로 구분)
          <input
            id="data-backfill-codes"
            value={backfillCodes}
            onChange={(event) => setBackfillCodes(event.target.value)}
            placeholder="비우면 국내 전종목"
            className={`mt-1 ${INPUT_CLASS}`}
          />
        </label>
        <label className="block text-sm font-semibold text-gray-700" htmlFor="data-backfill-days">
          최근 며칠(선택)
          <input
            id="data-backfill-days"
            type="number"
            min={1}
            value={backfillDays}
            onChange={(event) => setBackfillDays(event.target.value)}
            placeholder="비우면 20일"
            className={`mt-1 ${INPUT_CLASS}`}
          />
        </label>
      </AdminActionCard>

      <AdminActionCard
        title="7. 스코어 재구성"
        description="선택한 날짜 이후의 스코어를 전부 지우고 전 상장 종목을 다시 계산합니다. 끝난 뒤에는 3번(가격과 스코어 채우기)을 한 번 실행해야 순위 정규화가 반영돼요."
        buttonLabel="재구성 실행"
        tone="danger"
        disabled={!rebuildFrom}
        confirm={{
          title: '스코어를 지우고 다시 계산할까요?',
          description: `${rebuildFrom} 이후 스코어를 먼저 지워요. 계산이 중간에 실패하면 그 구간의 스코어가 비어 있게 되지만, 배치 실행 화면에서 이어서 실행할 수 있어요.`,
        }}
        run={() => rebuildScores(rebuildFrom)}
        renderResult={(text) => <BatchStartedResult text={text} />}
      >
        <label className="block text-sm font-semibold text-gray-700" htmlFor="data-rebuild-from">
          시작일
          <input
            id="data-rebuild-from"
            type="date"
            value={rebuildFrom}
            onChange={(event) => setRebuildFrom(event.target.value)}
            className={`mt-1 ${INPUT_CLASS}`}
          />
        </label>
      </AdminActionCard>
    </div>
  )
}
