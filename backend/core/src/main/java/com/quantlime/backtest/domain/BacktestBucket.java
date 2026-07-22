package com.quantlime.backtest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static lombok.AccessLevel.PROTECTED;

/**
 * 스코어 5분위 버킷 하나의 백테스트 통계(평균/중위 초과수익률, 히트레이트,
 * 표본수). {@link BacktestResult} 하나당 5개가 붙는다 - 별도 엔티티/리포지토리
 * 없이 {@code @ElementCollection}으로 저장되는 값 객체다(자식 행 5개 고정,
 * 독립적으로 조회될 일이 없어 풀 엔티티로 승격할 이유가 없음).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = PROTECTED)
public class BacktestBucket {

    @Column(name = "bucket_number", nullable = false)
    private int bucketNumber;

    @Column(name = "mean_excess_return")
    private Double meanExcessReturn;

    @Column(name = "median_excess_return")
    private Double medianExcessReturn;

    @Column(name = "hit_rate")
    private Double hitRate;

    @Column(name = "sample_size", nullable = false)
    private int sampleSize;

    @Builder
    private BacktestBucket(int bucketNumber, Double meanExcessReturn,
                           Double medianExcessReturn, Double hitRate, int sampleSize) {
        Assert.isTrue(bucketNumber >= 1, "버킷 번호는 1 이상이어야 합니다.");
        this.bucketNumber = bucketNumber;
        this.meanExcessReturn = meanExcessReturn;
        this.medianExcessReturn = medianExcessReturn;
        this.hitRate = hitRate;
        this.sampleSize = sampleSize;
    }

    public static BacktestBucket of(int bucketNumber, Double meanExcessReturn,
                                    Double medianExcessReturn, Double hitRate, int sampleSize) {
        return BacktestBucket.builder()
            .bucketNumber(bucketNumber)
            .meanExcessReturn(meanExcessReturn)
            .medianExcessReturn(medianExcessReturn)
            .hitRate(hitRate)
            .sampleSize(sampleSize)
            .build();
    }
}
