package com.quantlime.infra.kis.dto;

/**
 * KIS 해외주식 종목정보 마스터파일(.mst.cod) 한 행. 실제 파일 구조를
 * 다운로드해 직접 확인한 컬럼 순서(탭 구분, CP949, 24개 컬럼) 기준:
 * [0]국가코드 [1]거래소ID [2]거래소코드 [3]거래소한글명 [4]종목코드
 * [5]거래소+종목코드 [6]한글종목명 [7]영문종목명 [8]종목구분(1:지수,
 * 2:주식, 3:ETP/ETF, 4:Warrant) ... [19]업종코드. 이 레코드는 유니버스
 * 선정에 필요한 최소 필드(종목코드, 영문명, 종목구분, 업종코드)만 담는다.
 */
public record KisOverseasStockMasterEntry(
    String symbol,
    String englishName,
    String securityType,
    String industryCode
) {

    private static final String SECURITY_TYPE_STOCK = "2";
    // [19]업종코드는 KIS가 공식 문서화하지 않았으나, 실제 마스터파일 다운로드로
    // 확인한 결과 REIT(Realty Income/AMT/PLD/PSA/Simon Property 등)와 부동산
    // 서비스업(CBRE/RE MAX)이 전부 "630"으로, 그 외 업종(IBM=720, JPM=610,
    // XOM=010 등)은 전부 다른 코드로 확인됐다(Phase C 백테스트 유니버스 - REIT만
    // 정밀 타겟팅은 못 하지만 "부동산 섹터 제외"라는 목적에는 부합, 국내 REIT
    // 이름 매칭 필터의 해외판). OverseasUniverseSelectionService가 이 값을
    // Stock.sector에 저장된 채로 필터링한다.
    private static final String INDUSTRY_CODE_REAL_ESTATE = "630";

    public boolean isStock() {
        return SECURITY_TYPE_STOCK.equals(securityType);
    }

    public boolean isRealEstateSector() {
        return INDUSTRY_CODE_REAL_ESTATE.equals(industryCode);
    }
}
