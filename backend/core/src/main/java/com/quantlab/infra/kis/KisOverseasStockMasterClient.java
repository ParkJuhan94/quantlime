package com.quantlab.infra.kis;

import com.quantlab.common.util.ExternalApiInvoker;
import com.quantlab.infra.kis.dto.KisOverseasStockMasterEntry;
import com.quantlab.infra.kis.exception.KisApiErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * KIS 해외주식 종목정보 마스터파일(예: {@code nasmst.cod.zip}) 다운로드·파싱.
 * 문서화된 JSON API가 아니라 zip 압축된 CP949 탭 구분 텍스트 파일이다
 * (실제 다운로드로 확인 - KIND 상장법인목록이 HTML 테이블이었던 것과 같은
 * 종류의 "비-JSON 정적 파일" 연동). 24개 컬럼 중 유니버스 선정에 필요한
 * 종목코드(5번째)·영문종목명(8번째)·종목구분(9번째, 1:지수/2:주식/
 * 3:ETP·ETF/4:Warrant)만 사용한다.
 */
@Component
@RequiredArgsConstructor
public class KisOverseasStockMasterClient {

    private static final Charset MASTER_FILE_CHARSET = Charset.forName("CP949");
    private static final int COLUMN_SYMBOL = 4;
    private static final int COLUMN_ENGLISH_NAME = 7;
    private static final int COLUMN_SECURITY_TYPE = 8;
    private static final int MIN_COLUMN_COUNT = COLUMN_SECURITY_TYPE + 1;

    private final RestClient kisMasterFileRestClient;

    /**
     * exchangeCode 예: nas(나스닥), nys(뉴욕), ams(아멕스).
     */
    public List<KisOverseasStockMasterEntry> fetchStockMaster(String exchangeCode) {
        return ExternalApiInvoker.call(
            KisApiErrorCode.MASTER_FILE_DOWNLOAD_FAILED,
            () -> {
                byte[] zipBytes = kisMasterFileRestClient.get()
                    .uri("/common/master/{code}mst.cod.zip", exchangeCode)
                    .retrieve()
                    .body(byte[].class);
                return parseMasterFile(zipBytes);
            });
    }

    private List<KisOverseasStockMasterEntry> parseMasterFile(byte[] zipBytes) {
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zipIn.getNextEntry();
            if (entry == null) {
                return List.of();
            }
            String content = new String(zipIn.readAllBytes(), MASTER_FILE_CHARSET);

            List<KisOverseasStockMasterEntry> entries = new ArrayList<>();
            for (String line : content.split("\n")) {
                parseLine(line).ifPresent(entries::add);
            }
            return entries;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<KisOverseasStockMasterEntry> parseLine(String line) {
        String[] columns = line.split("\t", -1);
        if (columns.length < MIN_COLUMN_COUNT) {
            return Optional.empty();
        }
        return Optional.of(new KisOverseasStockMasterEntry(
            columns[COLUMN_SYMBOL].trim(),
            columns[COLUMN_ENGLISH_NAME].trim(),
            columns[COLUMN_SECURITY_TYPE].trim()));
    }
}
