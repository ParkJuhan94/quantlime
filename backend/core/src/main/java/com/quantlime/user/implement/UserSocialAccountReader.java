package com.quantlime.user.implement;

import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.UserSocialAccount;
import com.quantlime.user.repository.UserSocialAccountRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 연결된 소셜 계정 조회를 감싸는 구현 레이어. 메서드 이름은 Repository와 같다. */
@Component
@RequiredArgsConstructor
public class UserSocialAccountReader {

    private final UserSocialAccountRepository userSocialAccountRepository;

    public Optional<UserSocialAccount> findByProviderAndProviderId(OAuthProvider provider, String providerId) {
        return userSocialAccountRepository.findByProviderAndProviderId(provider, providerId);
    }

    public List<UserSocialAccount> findAllByUser_Id(Long userId) {
        return userSocialAccountRepository.findAllByUser_Id(userId);
    }

    public Optional<UserSocialAccount> findByUser_IdAndProvider(Long userId, OAuthProvider provider) {
        return userSocialAccountRepository.findByUser_IdAndProvider(userId, provider);
    }
}
