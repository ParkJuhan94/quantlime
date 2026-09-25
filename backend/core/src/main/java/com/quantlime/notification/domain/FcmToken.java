package com.quantlime.notification.domain;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static jakarta.persistence.ConstraintMode.NO_CONSTRAINT;
import static lombok.AccessLevel.PROTECTED;

// 사용자당 여러 기기(브라우저) 토큰을 동시에 보관한다(데스크톱+모바일
// 동시 로그인 지원) - 같은 토큰 문자열이 다른 계정으로 재등록되면(같은
// 브라우저에서 계정 전환 등) 새 row를 만들지 않고 reassignTo로 소유자만
// 교체한다(FcmTokenService.registerToken 참고).
@Entity
@Table(name = "fcm_token", uniqueConstraints = @UniqueConstraint(
    name = "uk_fcm_token_token", columnNames = "token"))
@Getter
@NoArgsConstructor(access = PROTECTED)
public class FcmToken extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fcm_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
        foreignKey = @ForeignKey(NO_CONSTRAINT))
    private User user;

    @Column(name = "token", nullable = false, length = 255)
    private String token;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    @Builder
    private FcmToken(User user, String token, String deviceInfo) {
        validateFcmToken(user, token);
        this.user = user;
        this.token = token;
        this.deviceInfo = deviceInfo;
    }

    public static FcmToken of(User user, String token, String deviceInfo) {
        return FcmToken.builder()
            .user(user)
            .token(token)
            .deviceInfo(deviceInfo)
            .build();
    }

    public void reassignTo(User user) {
        Assert.notNull(user, "사용자는 필수입니다.");
        this.user = user;
    }

    private void validateFcmToken(User user, String token) {
        Assert.notNull(user, "사용자는 필수입니다.");
        Assert.hasText(token, "FCM 토큰은 필수입니다.");
    }
}
