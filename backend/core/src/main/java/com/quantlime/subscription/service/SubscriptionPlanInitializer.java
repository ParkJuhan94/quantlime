package com.quantlime.subscription.service;

import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 월 기준가(7,900원)에 개월수를 곱한 값을 할인 없이 그대로 시딩한다 -
// 실제 할인 정책이 정해지면 이 클래스의 상수만 바꾸고 재기동하면
// 반영된다. Flyway/Liquibase 없이 ddl-auto=update만 쓰는 프로젝트라
// StockMasterInitializer와 동일한 ApplicationRunner 방식을 그대로 따름
// (이미 있는 코드는 skip, 없는 코드만 insert).
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class SubscriptionPlanInitializer implements ApplicationRunner {

    private static final int MONTHLY_PRICE_WON = 7900;

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIfAbsent("PLAN_3M", "3개월 플랜", 3);
        seedIfAbsent("PLAN_6M", "6개월 플랜", 6);
        seedIfAbsent("PLAN_12M", "12개월 플랜", 12);
    }

    private void seedIfAbsent(String code, String name, int months) {
        if (subscriptionPlanRepository.existsByCode(code)) {
            return;
        }
        int priceWon = MONTHLY_PRICE_WON * months;
        subscriptionPlanRepository.save(SubscriptionPlan.of(code, name, months, priceWon));
        log.info("구독 플랜 시딩 완료: code={}, months={}, priceWon={}", code, months, priceWon);
    }
}
