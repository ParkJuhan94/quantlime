package com.quantlab.watchlist.domain;

import com.quantlab.common.domain.TimeBaseEntity;
import com.quantlab.stock.domain.Stock;
import com.quantlab.user.domain.User;
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

    @Builder
    private Watchlist(User user, Stock stock) {
        validateWatchlist(user, stock);
        this.user = user;
        this.stock = stock;
    }

    public static Watchlist of(User user, Stock stock) {
        return Watchlist.builder()
            .user(user)
            .stock(stock)
            .build();
    }

    private void validateWatchlist(User user, Stock stock) {
        Assert.notNull(user, "사용자는 필수입니다.");
        Assert.notNull(stock, "종목은 필수입니다.");
    }
}
