package com.quantlime.watchlist.domain;

import static jakarta.persistence.ConstraintMode.NO_CONSTRAINT;
import static lombok.AccessLevel.PROTECTED;

import com.quantlime.common.domain.TimeBaseEntity;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

@Entity
@Table(name = "watchlist_group")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class WatchlistGroup extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "watchlist_group_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
        foreignKey = @ForeignKey(NO_CONSTRAINT))
    private User user;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // 그룹 단위 옵트인 - 켜진 그룹의 종목만 사분면 변화 알림 대상이 된다
    // (QuadrantChangeAlertService). 기본값 꺼짐.
    @Column(name = "quadrant_alert_enabled", nullable = false)
    private boolean quadrantAlertEnabled;

    @Builder
    private WatchlistGroup(User user, String name, int sortOrder) {
        validateWatchlistGroup(user, name);
        this.user = user;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public static WatchlistGroup of(User user, String name, int sortOrder) {
        return WatchlistGroup.builder()
            .user(user)
            .name(name)
            .sortOrder(sortOrder)
            .build();
    }

    public void rename(String name) {
        Assert.hasText(name, "그룹 이름은 필수입니다.");
        this.name = name;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void changeQuadrantAlert(boolean enabled) {
        this.quadrantAlertEnabled = enabled;
    }

    private void validateWatchlistGroup(User user, String name) {
        Assert.notNull(user, "사용자는 필수입니다.");
        Assert.hasText(name, "그룹 이름은 필수입니다.");
    }
}
