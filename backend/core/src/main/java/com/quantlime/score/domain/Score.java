package com.quantlime.score.domain;

import com.quantlime.common.domain.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "score",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_score_stock_date",
        columnNames = {"stock_code", "score_date"}
    ),
    // uk_score_stock_date(stock_code, score_date)와 컬럼 구성이 완전히 같던
    // idx_score_stock_date(stock_code, score_date DESC)는 제거했다(2026-09
    // 성능 감사 - 117MB, performance_schema 기준 2.3일간 읽기 0회). MySQL은
    // 오름차순 인덱스를 역방향으로도 스캔할 수 있어(EXPLAIN상 "Index lookup
    // ... (reverse)") 단일 컬럼 정렬만으로는 별도 DESC 인덱스가 필요 없다 -
    // findTopByStockCodeOrderByTradeDateDesc류 최신행 조회는 UK로 그대로
    // 커버된다.
    indexes = {
        // /api/dashboard/scores(전체 랭킹) 쿼리가 튜플 IN 서브쿼리로
        // "종목별 최신 score_date"만 걸러낸 뒤 ORDER BY composite_score DESC
        // LIMIT 50을 도는데, 이 인덱스가 없으면 옵티마이저가 전체 후보를
        // 스캔한 뒤 정렬한다. 2026-08-19 실측: 이 인덱스 추가로 옵티마이저가
        // composite_score 인덱스를 내림차순으로 훑으며 조건을 만족하는 50개를
        // 찾는 즉시 멈추는 전략(EXPLAIN상 rows=50)으로 바뀌어 상관 서브쿼리
        // 대비 약 100배(116.7초→~1초 내외) 단축됐다(성능 개선 계획 문서 B1 참고).
        @Index(
            name = "idx_score_composite_score",
            columnList = "composite_score DESC"
        ),
        // v3.0부터 랭킹 정렬은 composite_score(절대점수)가 아니라
        // composite_percentile(횡단면 백분위)을 쓴다 - 위 idx_score_composite_score
        // 와 동일한 이유(옵티마이저가 인덱스를 내림차순으로 훑으며 LIMIT까지만
        // 조기 종료하게 함)로 별도 인덱스를 둔다. 원점수 기준 조회(백테스트
        // 등)는 기존 인덱스를 그대로 쓴다.
        @Index(
            name = "idx_score_composite_percentile",
            columnList = "composite_percentile DESC"
        ),
        // /api/dashboard/scores(watchlistOnly=false) 전체 랭킹 쿼리가
        // 2026-09 성능 감사로 "종목별 최신일" 튜플 IN 서브쿼리 대신 "전
        // 배치 최신 score_date 하나"로 필터링하도록 바뀌면서(ScoreQueryRepositoryImpl
        // 참고) score_date 등치 조건 + composite_percentile 정렬을 함께
        // 만족하는 복합 인덱스가 유효해졌다 - MySQL이 score_date로 범위를
        // 좁힌 뒤 그 안에서 이미 percentile 내림차순으로 정렬된 상태로
        // LIMIT까지만 훑을 수 있다. 위 단일 컬럼 idx_score_composite_percentile은
        // 이 인덱스 도입 후 실사용을 performance_schema로 재확인해 읽기
        // 0이면 별도로 제거할 것(다른 호출부가 없다면 완전히 중복이 된다).
        @Index(
            name = "idx_score_date_composite_percentile",
            columnList = "score_date, composite_percentile DESC"
        )
    }
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Score extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "score_id")
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Column(name = "score_date", nullable = false)
    private LocalDate scoreDate;

    @Column(name = "trend_score")
    private Double trendScore;

    @Column(name = "mean_reversion_score")
    private Double meanReversionScore;

    @Column(name = "composite_score")
    private Double compositeScore;

    // Hibernate가 @Enumerated(STRING)을 MySQL 네이티브 enum(...) 컬럼으로
    // 매핑해와서, Grade 상수 이름이 바뀔 때마다(SSS~D → 5단계) 기존 행에 남은
    // 옛 값이 새 enum 값 목록에 없어 ALTER TABLE 자체가 "Data truncated"로
    // 실패했다(레거시 값이 남아있는 한 매 기동마다 반복 재현됨) - columnDefinition을
    // 명시해 항상 varchar로 고정한다. enum→varchar 전환은 값 목록 제약이
    // 없어 레거시 데이터가 남아 있어도 안전하게 끝난다.
    @Enumerated(EnumType.STRING)
    @Column(name = "grade", columnDefinition = "varchar(20)")
    private Grade grade;

    // Grade와 동일한 이유로(§58-63 주석 참고) columnDefinition을 varchar로
    // 고정한다 - 사분면 라벨(Quadrant) 자체가 새로 추가된 값이라 지금은
    // 레거시 데이터 문제가 없지만, 향후 상수 이름이 바뀔 가능성에 대비해
    // 처음부터 동일 패턴으로 통일한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "quadrant", columnDefinition = "varchar(30)")
    private Quadrant quadrant;

    @Embedded
    private Divergence divergence;

    @Column(name = "insufficient_data", nullable = false)
    private boolean insufficientData;

    // v3.0부터 도입 - 절대 서브스코어를 같은 날짜·같은 모집단(국내/해외)
    // 안에서 상대 순위(0~100, 높을수록 상위)로 바꾼 값. 랭킹 정렬·등급은
    // 이 값을 쓴다(quant-engine `/normalize/cross-section` 산출, 별도
    // 트리거로 raw score 저장 이후에 채워지므로 초기엔 null일 수 있다).
    @Column(name = "trend_percentile")
    private Double trendPercentile;

    @Column(name = "mean_reversion_percentile")
    private Double meanReversionPercentile;

    @Column(name = "composite_percentile")
    private Double compositePercentile;

    // Grade와 동일한 이유로 columnDefinition을 varchar로 고정한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "peer_group", columnDefinition = "varchar(20)")
    private PeerGroup peerGroup;

    @Builder
    private Score(String stockCode, LocalDate scoreDate, Double trendScore,
                  Double meanReversionScore, Double compositeScore, Grade grade,
                  Quadrant quadrant, Divergence divergence,
                  boolean insufficientData) {
        validateScore(stockCode, scoreDate);
        this.stockCode = stockCode;
        this.scoreDate = scoreDate;
        this.trendScore = trendScore;
        this.meanReversionScore = meanReversionScore;
        this.compositeScore = compositeScore;
        this.grade = grade;
        this.quadrant = quadrant;
        this.divergence = divergence;
        this.insufficientData = insufficientData;
    }

    public static Score of(String stockCode, LocalDate scoreDate, Double trendScore,
                           Double meanReversionScore, Double compositeScore, Grade grade,
                           Quadrant quadrant, Divergence divergence,
                           boolean insufficientData) {
        return Score.builder()
            .stockCode(stockCode)
            .scoreDate(scoreDate)
            .trendScore(trendScore)
            .meanReversionScore(meanReversionScore)
            .compositeScore(compositeScore)
            .grade(grade)
            .quadrant(quadrant)
            .divergence(divergence)
            .insufficientData(insufficientData)
            .build();
    }

    /**
     * 같은 날 재계산이 발생했을 때(예: 관심 종목 등록 직후 + 이후 수동 재계산이
     * 겹치는 경우) 새 행을 또 만들지 않고 기존 당일 행의 값을 갱신한다.
     * setter를 두지 않는 컨벤션을 지키기 위한 비즈니스 메서드.
     *
     * <p>{@code grade}는 quant-engine의 {@code calculate_score}가 raw
     * composite_score(절대점수) 기준으로 즉시 매겨 내려주는 값이라, 이
     * 메서드 호출만으로 바로 유효한 등급이 채워진다 - 관심종목 등록 시 단건
     * 재계산({@code WatchlistService}) 등 배치 밖 경로에서도 다음 배치를
     * 기다릴 필요가 없다. percentile 필드는 이 메서드가 건드리지 않고 그대로
     * 남는다 - 그 값은 {@link #applyNormalization}(횡단면 정규화, 배치
     * 전용)이 별도로 관리한다.
     */
    public void updateFrom(Double trendScore, Double meanReversionScore,
                           Double compositeScore, Grade grade, Quadrant quadrant,
                           Divergence divergence, boolean insufficientData) {
        this.trendScore = trendScore;
        this.meanReversionScore = meanReversionScore;
        this.compositeScore = compositeScore;
        this.grade = grade;
        this.quadrant = quadrant;
        this.divergence = divergence;
        this.insufficientData = insufficientData;
    }

    /**
     * 횡단면 정규화 단계(quant-engine `/normalize/cross-section`)의 결과로
     * 백분위·모집단을 채운다. 등급(grade)은 이 메서드가 건드리지 않는다 -
     * 등급은 "이 종목이 절대 기준으로 매수할 만한가"를 뜻해야 하는데, 횡단면
     * 백분위 기준으로 매기면 시장 전체가 나쁜 날에도 상위 10%가 기계적으로
     * 항상 STRONG_BUY가 되는 설계 결함이 있었다(2026-09 재검토) - 등급은
     * {@link #updateFrom}이 이미 절대점수로 채워둔 값을 그대로 유지한다.
     * raw 서브스코어(trendScore 등)도 건드리지 않는다 - 그 값은
     * {@link #updateFrom}(스코어 재계산)이 별도로 관리한다. setter를 두지
     * 않는 컨벤션을 지키기 위한 비즈니스 메서드.
     */
    public void applyNormalization(Double trendPercentile, Double meanReversionPercentile,
                                   Double compositePercentile, PeerGroup peerGroup) {
        this.trendPercentile = trendPercentile;
        this.meanReversionPercentile = meanReversionPercentile;
        this.compositePercentile = compositePercentile;
        this.peerGroup = peerGroup;
    }

    private void validateScore(String stockCode, LocalDate scoreDate) {
        Assert.hasText(stockCode, "종목 코드는 필수입니다.");
        Assert.notNull(scoreDate, "스코어 산출일은 필수입니다.");
    }
}
