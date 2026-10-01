package com.quantlime.user.domain;

import static jakarta.persistence.ConstraintMode.NO_CONSTRAINT;
import static lombok.AccessLevel.PROTECTED;

import com.quantlime.common.domain.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * 가입에 쓴 소셜 계정({@link User#getProvider()})과 별개로, 로그인한 상태에서 추가로 연결한 소셜 계정.
 * 이메일 일치만으로 자동 병합하면 계정 탈취 벡터가 되므로(ROADMAP "소셜 로그인 계정 병합") 반드시
 * 로그인된 사용자가 추가 소셜 인증(소유 확인)을 거친 뒤에만 이 행이 만들어진다. 같은 소셜 계정
 * (provider+providerId)은 전체에서 하나의 사용자에게만 속할 수 있다.
 */
@Entity
@Table(name = "user_social_account", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_social_provider_provider_id", columnNames = {"provider", "provider_id"}),
    @UniqueConstraint(name = "uk_user_social_user_provider", columnNames = {"user_id", "provider"})
})
@Getter
@NoArgsConstructor(access = PROTECTED)
public class UserSocialAccount extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_social_account_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(NO_CONSTRAINT))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 10)
    private OAuthProvider provider;

    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    @Builder
    private UserSocialAccount(User user, OAuthProvider provider, String providerId) {
        Assert.notNull(user, "사용자는 필수입니다.");
        Assert.notNull(provider, "소셜 로그인 제공자는 필수입니다.");
        Assert.hasText(providerId, "소셜 로그인 식별자는 필수입니다.");
        this.user = user;
        this.provider = provider;
        this.providerId = providerId;
    }

    public static UserSocialAccount of(User user, OAuthProvider provider, String providerId) {
        return UserSocialAccount.builder().user(user).provider(provider).providerId(providerId).build();
    }
}
