package com.quantlime.price.implement;

import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 국내/해외 일봉 저장을 감싸는 구현 레이어(Implementation) - 신규 저장과 기존 행
 * 갱신(dirty 엔티티 재저장) 모두 이 컴포넌트를 거친다. 건별 커밋·중복 스킵 정책은
 * 호출부(백필 서비스)가 정한다.
 */
@Component
@RequiredArgsConstructor
public class DailyPriceAppender {

    private final DomesticDailyPriceRepository domesticDailyPriceRepository;
    private final OverseasDailyPriceRepository overseasDailyPriceRepository;

    public void saveDomestic(DomesticDailyPrice price) {
        domesticDailyPriceRepository.save(price);
    }

    public void saveAllDomestic(List<DomesticDailyPrice> prices) {
        domesticDailyPriceRepository.saveAll(prices);
    }

    public void saveOverseas(OverseasDailyPrice price) {
        overseasDailyPriceRepository.save(price);
    }
}
