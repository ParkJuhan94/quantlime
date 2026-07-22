package com.quantlime.subscription.domain;

import com.quantlime.common.domain.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "subscription_plan", uniqueConstraints = @UniqueConstraint(
    name = "uk_subscription_plan_code", columnNames = "code"))
@Getter
@NoArgsConstructor(access = PROTECTED)
public class SubscriptionPlan extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_plan_id")
    private Long id;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "billing_period_months", nullable = false)
    private int billingPeriodMonths;

    // 판매 단위(개월수) 전체 기간의 총액. 월 단가가 아니라 한 번에 청구되는
    // 총 결제 금액이다(예: 12개월 플랜이면 12개월치 전체 금액).
    @Column(name = "price_won", nullable = false)
    private int priceWon;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Builder
    private SubscriptionPlan(String code, String name, int billingPeriodMonths,
                              int priceWon, boolean active) {
        validatePlan(code, name, billingPeriodMonths, priceWon);
        this.code = code;
        this.name = name;
        this.billingPeriodMonths = billingPeriodMonths;
        this.priceWon = priceWon;
        this.active = active;
    }

    public static SubscriptionPlan of(String code, String name, int billingPeriodMonths, int priceWon) {
        return SubscriptionPlan.builder()
            .code(code)
            .name(name)
            .billingPeriodMonths(billingPeriodMonths)
            .priceWon(priceWon)
            .active(true)
            .build();
    }

    private void validatePlan(String code, String name, int billingPeriodMonths, int priceWon) {
        Assert.hasText(code, "플랜 코드는 필수입니다.");
        Assert.hasText(name, "플랜 이름은 필수입니다.");
        Assert.isTrue(billingPeriodMonths > 0, "결제 주기(개월)는 0보다 커야 합니다.");
        Assert.isTrue(priceWon > 0, "가격은 0보다 커야 합니다.");
    }
}
