package com.quantlab.user.domain;

import com.quantlab.common.domain.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "users", uniqueConstraints = @UniqueConstraint(
    name = "uk_user_provider_provider_id", columnNames = {"provider", "provider_id"}))
@Getter
@NoArgsConstructor(access = PROTECTED)
public class User extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 10)
    private OAuthProvider provider;

    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private UserRole role;

    @Builder
    private User(String email, String nickname, String profileImageUrl,
                 OAuthProvider provider, String providerId, UserRole role) {
        validateUser(nickname, provider, providerId, role);
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.provider = provider;
        this.providerId = providerId;
        this.role = role;
    }

    public static User of(String email, String nickname, String profileImageUrl,
                          OAuthProvider provider, String providerId) {
        return User.builder()
            .email(email)
            .nickname(nickname)
            .profileImageUrl(profileImageUrl)
            .provider(provider)
            .providerId(providerId)
            .role(UserRole.USER)
            .build();
    }

    public void updateProfile(String email, String nickname, String profileImageUrl) {
        Assert.hasText(nickname, "닉네임은 필수입니다.");
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    private void validateUser(String nickname, OAuthProvider provider,
                              String providerId, UserRole role) {
        Assert.hasText(nickname, "닉네임은 필수입니다.");
        Assert.notNull(provider, "소셜 로그인 제공자는 필수입니다.");
        Assert.hasText(providerId, "소셜 로그인 식별자는 필수입니다.");
        Assert.notNull(role, "사용자 권한은 필수입니다.");
    }
}
