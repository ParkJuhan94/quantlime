// 백테스트 방법론(quant-engine/docs/BACKTEST_METHODOLOGY_REVIEW.md)의
// 한계를 매 화면 고정으로 노출한다 - 결과를 과신하지 않도록 하는 안전장치.
export function BacktestDisclaimer() {
  return (
    <div className="rounded-xl border border-gray-100 bg-gray-50 p-4 text-[11px] leading-relaxed text-gray-500">
      <p className="mb-1 font-semibold text-gray-600">읽기 전 참고해주세요</p>
      <ul className="list-disc space-y-0.5 pl-4">
        <li>
          진입가는 스코어가 산출된 다음 거래일(T+1) 종가를 기준으로 계산했어요 - 스코어는 장 마감 후 나오므로
          당일 종가로 매매했다고 가정하면 아직 나오지 않은 정보로 거래한 셈이 됩니다.
        </li>
        <li>거래 비용(수수료·세금·슬리피지)은 반영하지 않았습니다.</li>
        <li>
          현재 백테스트 유니버스에 포함된 종목 위주의 결과라, 상장폐지되었거나 유니버스에서 제외된 종목은
          포함되지 않아요(생존/선택 편향).
        </li>
        <li>표본 수(N)가 적은 구간, 특히 분위수 버킷별 N&lt;10인 값은 통계적 신뢰도가 낮습니다.</li>
      </ul>
    </div>
  )
}
