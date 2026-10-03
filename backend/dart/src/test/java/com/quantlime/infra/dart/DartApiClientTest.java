package com.quantlime.infra.dart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.dart.dto.DartCorpInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@Tag("unit")
class DartApiClientTest {

    private static final String BASE_URL = "https://dart.test";

    private MockRestServiceServer mockServer;
    private DartApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new DartApiClient(builder.build(), new DartApiProperties("dart-key", BASE_URL));
    }

    private byte[] zipOf(String xml) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("CORPCODE.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private void respondWithZip(String xml) throws IOException {
        mockServer.expect(requestTo(Matchers.startsWith(BASE_URL + "/corpCode.xml")))
            .andExpect(queryParam("crtfc_key", "dart-key"))
            .andRespond(withSuccess(new ByteArrayResource(zipOf(xml)), MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test
    @DisplayName("[ZIP을 풀어 XML을 파싱하고 6자리 종목코드가 있는 상장 법인만 남긴다(비상장은 제외)]")
    void fetchCorpList_keepsOnlyListedCorporations() throws IOException {
        // given
        respondWithZip("""
            <?xml version="1.0" encoding="UTF-8"?>
            <result>
              <list><corp_code>00126380</corp_code><corp_name>삼성전자</corp_name><stock_code>005930</stock_code></list>
              <list><corp_code>00000001</corp_code><corp_name>비상장회사</corp_name><stock_code> </stock_code></list>
              <list><corp_code>00000002</corp_code><corp_name>코드이상</corp_name><stock_code>12345</stock_code></list>
              <list><corp_code>00164779</corp_code><corp_name>SK하이닉스</corp_name><stock_code>000660</stock_code></list>
            </result>
            """);

        // when
        List<DartCorpInfo> corps = client.fetchCorpList();

        // then
        assertThat(corps).containsExactly(
            new DartCorpInfo("00126380", "삼성전자", "005930"),
            new DartCorpInfo("00164779", "SK하이닉스", "000660"));
        mockServer.verify();
    }

    @Test
    @DisplayName("[status가 000이 아닌 에러 응답은 ExternalApiException으로 처리한다]")
    void fetchCorpList_errorStatus_throws() throws IOException {
        respondWithZip("""
            <?xml version="1.0" encoding="UTF-8"?>
            <result><status>020</status><message>요청 제한을 초과하였습니다.</message></result>
            """);

        assertThatThrownBy(() -> client.fetchCorpList()).isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("[status가 000이면 정상으로 파싱한다]")
    void fetchCorpList_status000_isOk() throws IOException {
        respondWithZip("""
            <?xml version="1.0" encoding="UTF-8"?>
            <result><status>000</status><message>정상</message>
              <list><corp_code>1</corp_code><corp_name>A</corp_name><stock_code>000001</stock_code></list>
            </result>
            """);

        assertThat(client.fetchCorpList()).hasSize(1);
    }

    @Test
    @DisplayName("[DOCTYPE이 포함된 XML(XXE 시도)은 거부한다]")
    void fetchCorpList_doctype_isRejected() throws IOException {
        respondWithZip("""
            <?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <result><list><corp_code>&xxe;</corp_code><corp_name>x</corp_name><stock_code>000001</stock_code></list></result>
            """);

        assertThatThrownBy(() -> client.fetchCorpList()).isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("[ZIP이 아니거나 비어 있는 응답은 ExternalApiException으로 감싼다]")
    void fetchCorpList_notAZip_throws() {
        mockServer.expect(requestTo(Matchers.startsWith(BASE_URL + "/corpCode.xml")))
            .andRespond(withSuccess("not-a-zip".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_OCTET_STREAM));

        assertThatThrownBy(() -> client.fetchCorpList()).isInstanceOf(ExternalApiException.class);
    }
}
