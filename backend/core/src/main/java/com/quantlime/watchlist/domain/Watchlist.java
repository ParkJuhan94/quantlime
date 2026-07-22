package com.quantlime.watchlist.domain;

import com.quantlime.common.domain.TimeBaseEntity;
import com.quantlime.stock.domain.Stock;
import com.quantlime.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static jakarta.persistence.ConstraintMode.NO_CONSTRAINT;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "watchlist", uniqueConstraints = @UniqueConstraint(
    name = "uk_watchlist_user_stock", columnNames = {"user_id", "stock_id"}))
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Watchlist extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "watchlist_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
        foreignKey = @ForeignKey(NO_CONSTRAINT))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false,
        foreignKey = @ForeignKey(NO_CONSTRAINT))
    private Stock stock;

    // 관심종목은 항상 어느 한 그룹에 속한다("미분류" 상태 폐지 - 2026-07-14
    // 세션에서 등록 시점에 그룹 선택을 강제하는 쪽으로 정리). DB 컬럼
    // 자체는 nullable로 남겨둔다 - ddl-auto=update 환경에서 NOT NULL로
    // 바꾸면 기존에 이미 미분류로 남아있던 행 때문에 애플리케이션이
    // 그 행을 정리(WatchlistService의 자동 재배정)할 기회를 갖기도 전에
    // 스키마 ALTER 자체가 실패한다. 불변식은 여기(생성자 검증)와
    // assignToGroup에서 애플리케이션 레벨로 강제한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "watchlist_group_id",
        foreignKey = @ForeignKey(NO_CONSTRAINT))
    private WatchlistGroup group;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    private Watchlist(User user, Stock stock, WatchlistGroup group, int sortOrder) {
        validateWatchlist(user, stock, group);
        this.user = user;
        this.stock = stock;
        this.group = group;
        this.sortOrder = sortOrder;
    }

    public static Watchlist of(User user, Stock stock, WatchlistGroup group, int sortOrder) {
        return Watchlist.builder()
            .user(user)
            .stock(stock)
            .group(group)
            .sortOrder(sortOrder)
            .build();
    }

    public void assignToGroup(WatchlistGroup group) {
        Assert.notNull(group, "그룹은 필수입니다.");
        this.group = group;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    private void validateWatchlist(User user, Stock stock, WatchlistGroup group) {
        Assert.notNull(user, "사용자는 필수입니다.");
        Assert.notNull(stock, "종목은 필수입니다.");
        Assert.notNull(group, "그룹은 필수입니다.");
    }
}
