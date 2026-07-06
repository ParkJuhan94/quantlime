package com.quantlab.infra.python;

import com.quantlab.common.exception.ExternalApiException;
import com.quantlab.infra.python.dto.ScoreBatchApiRequest;
import com.quantlab.infra.python.dto.ScoreBatchApiResponse;
import com.quantlab.infra.python.exception.PythonEngineErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class PythonEngineClient {

    private final RestClient pythonEngineRestClient;

    public ScoreBatchApiResponse calculateScoreBatch(ScoreBatchApiRequest request) {
        try {
            ScoreBatchApiResponse response = pythonEngineRestClient.post()
                .uri("/calculate/score/batch")
                .body(request)
                .retrieve()
                .body(ScoreBatchApiResponse.class);

            if (response == null) {
                throw new ExternalApiException(PythonEngineErrorCode.SCORE_CALCULATION_FAILED);
            }

            return response;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException(PythonEngineErrorCode.SCORE_CALCULATION_FAILED, e);
        }
    }
}
