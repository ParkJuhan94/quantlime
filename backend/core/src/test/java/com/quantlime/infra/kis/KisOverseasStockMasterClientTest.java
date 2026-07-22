package com.quantlime.infra.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quantlime.infra.kis.dto.KisOverseasStockMasterEntry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@Tag("unit")
class KisOverseasStockMasterClientTest {

    private static final String BASE_URL = "https://kis-master.test";
    private static final Charset CP949 = Charset.forName("CP949");

    private MockRestServiceServer mockServer;
    private KisOverseasStockMasterClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        client = new KisOverseasStockMasterClient(restClient);
    }

    @Test
    @DisplayName("[정상 주식(종목구분 2)만 담고 ETF(3)는 그대로 전달한다 - 필터링은 호출측 책임]")
    void fetchStockMaster_parsesTabSeparatedCp949File() throws IOException {
        // given: 실제 KIS 마스터파일과 동일한 컬럼 구조(24컬럼 - 종목코드=5번째,
        // 영문명=8번째, 종목구분=9번째, 업종코드=20번째 위치만 정확히 맞춤,
        // 나머지는 생략 가능한 컬럼도 빈 값으로 채워 총 24컬럼 유지)
        String stockLine = "US\t21\tNYS\t뉴욕\tAA\tNYSAA\t알코아\tALCOA CORPORATION\t2\tUSD\t4\t\t65.18\t1\t1\t930\t1600\tN\t\t720\t0\t0\t\t";
        String etfLine = "US\t22\tNAS\t나스닥\tAAAP\tNASAAAP\tPACER ETF\tPACER BARINGS CLO ETF\t3\tUSD\t4\t\t25.00\t1\t1\t930\t1600\tN\t\t000\t0\t0\t\t";
        byte[] zipBytes = zipOf("NYSMST.COD", stockLine + "\n" + etfLine + "\n");

        mockServer.expect(requestTo(BASE_URL + "/common/master/nysmst.cod.zip"))
            .andRespond(withSuccess(new ByteArrayResource(zipBytes), MediaType.APPLICATION_OCTET_STREAM));

        // when
        List<KisOverseasStockMasterEntry> entries = client.fetchStockMaster("nys");

        // then
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).symbol()).isEqualTo("AA");
        assertThat(entries.get(0).englishName()).isEqualTo("ALCOA CORPORATION");
        assertThat(entries.get(0).isStock()).isTrue();
        assertThat(entries.get(0).industryCode()).isEqualTo("720");
        assertThat(entries.get(0).isRealEstateSector()).isFalse();
        assertThat(entries.get(1).isStock()).isFalse();
        mockServer.verify();
    }

    @Test
    @DisplayName("[업종코드가 630(부동산)이면 isRealEstateSector가 true를 반환한다]")
    void fetchStockMaster_flagsRealEstateSector() throws IOException {
        // given: 실제 다운로드로 확인한 Realty Income(REIT)의 업종코드(630) 재현
        String reitLine = "US\t21\tNYS\t뉴욕\tO\tNYSO\t리얼티 인컴\tREALTY INCOME CORP\t2\tUSD\t4\t\t65.18\t1\t1\t930\t1600\tN\t\t630\t0\t0\t\t";
        byte[] zipBytes = zipOf("NYSMST.COD", reitLine + "\n");

        mockServer.expect(requestTo(BASE_URL + "/common/master/nysmst.cod.zip"))
            .andRespond(withSuccess(new ByteArrayResource(zipBytes), MediaType.APPLICATION_OCTET_STREAM));

        // when
        List<KisOverseasStockMasterEntry> entries = client.fetchStockMaster("nys");

        // then
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).industryCode()).isEqualTo("630");
        assertThat(entries.get(0).isRealEstateSector()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("[컬럼 수가 부족한 행은 건너뛴다]")
    void fetchStockMaster_skipsMalformedLines() throws IOException {
        // given
        byte[] zipBytes = zipOf("NASMST.COD", "malformed\tline\n");
        mockServer.expect(requestTo(BASE_URL + "/common/master/nasmst.cod.zip"))
            .andRespond(withSuccess(new ByteArrayResource(zipBytes), MediaType.APPLICATION_OCTET_STREAM));

        // when
        List<KisOverseasStockMasterEntry> entries = client.fetchStockMaster("nas");

        // then
        assertThat(entries).isEmpty();
        mockServer.verify();
    }

    private byte[] zipOf(String entryName, String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes(CP949));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
