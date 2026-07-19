package com.quantlab.infra.kis.dto;

/**
 * KIS 해외주식 종목정보 마스터파일(.mst.cod) 한 행. 실제 파일 구조를
 * 다운로드해 직접 확인한 컬럼 순서(탭 구분, CP949, 24개 컬럼) 기준:
 * [0]국가코드 [1]거래소ID [2]거래소코드 [3]거래소한글명 [4]종목코드
 * [5]거래소+종목코드 [6]한글종목명 [7]영문종목명 [8]종목구분(1:지수,
 * 2:주식, 3:ETP/ETF, 4:Warrant) ... 이 레코드는 유니버스 선정에 필요한
 * 최소 필드(종목코드, 영문명, 종목구분)만 담는다.
 */
public record KisOverseasStockMasterEntry(
    String symbol,
    String englishName,
    String securityType
) {

    private static final String SECURITY_TYPE_STOCK = "2";

    public boolean isStock() {
        return SECURITY_TYPE_STOCK.equals(securityType);
    }
}
