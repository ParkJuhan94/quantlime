package com.quantlime.user.service;

import com.quantlime.auth.exception.AuthErrorCode;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.infra.oauth.dto.OAuthUserInfo;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.domain.UserSocialAccount;
import com.quantlime.user.dto.response.LinkedProviderResponse;
import com.quantlime.user.exception.UserErrorCode;
import com.quantlime.user.repository.UserRepository;
import com.quantlime.user.repository.UserSocialAccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;

    @Transactional
    public User findOrCreate(OAuthUserInfo userInfo) {
        return userRepository.findByProviderAndProviderId(
                userInfo.provider(), userInfo.providerId())
            .map(user -> {
                user.updateProfile(userInfo.email(), userInfo.nickname(),
                    userInfo.profileImageUrl());
                return user;
            })
            // 가입 계정이 아니라 나중에 연결한 소셜 계정으로 로그인한 경우 - 프로필은 가입 계정 기준을
            // 유지한다(연결 계정의 닉네임/사진으로 덮어쓰면 로그인 수단에 따라 프로필이 바뀐다).
            .or(() -> userSocialAccountRepository
                .findByProviderAndProviderId(userInfo.provider(), userInfo.providerId())
                .map(UserSocialAccount::getUser))
            .orElseGet(() -> {
                User user = User.of(userInfo.email(), userInfo.nickname(),
                    userInfo.profileImageUrl(), userInfo.provider(), userInfo.providerId());
                User saved = userRepository.save(user);
                log.info("신규 사용자 가입 완료: userId={}, provider={}",
                    saved.getId(), saved.getProvider());
                return saved;
            });
    }

    /**
     * 로그인된 사용자에게 추가 소셜 계정을 연결한다. 호출 전에 해당 소셜 계정의 소유를 OAuth 인가 코드
     * 교환으로 이미 확인한 {@code userInfo}여야 한다(이메일만으로 합치지 않는다).
     * 이미 같은 계정이 연결돼 있으면 조용히 성공(멱등), 다른 사용자에게 속한 계정이거나 같은
     * 제공자의 다른 계정이 이미 연결돼 있으면 거부한다(데이터 병합은 하지 않는다).
     */
    @Transactional
    public void linkSocialAccount(Long userId, OAuthUserInfo userInfo) {
        User user = getById(userId);

        if (user.getProvider() == userInfo.provider()) {
            if (user.getProviderId().equals(userInfo.providerId())) {
                return;
            }
            throw new ValidationException(AuthErrorCode.PROVIDER_ALREADY_LINKED);
        }

        Optional<User> primaryOwner = userRepository.findByProviderAndProviderId(
            userInfo.provider(), userInfo.providerId());
        if (primaryOwner.isPresent()) {
            // 다른 사용자의 가입 계정(자기 자신은 위에서 걸러짐).
            throw new ValidationException(AuthErrorCode.SOCIAL_ACCOUNT_IN_USE);
        }

        Optional<UserSocialAccount> linkedOwner = userSocialAccountRepository
            .findByProviderAndProviderId(userInfo.provider(), userInfo.providerId());
        if (linkedOwner.isPresent()) {
            if (linkedOwner.get().getUser().getId().equals(userId)) {
                return;
            }
            throw new ValidationException(AuthErrorCode.SOCIAL_ACCOUNT_IN_USE);
        }

        if (userSocialAccountRepository.findByUser_IdAndProvider(userId, userInfo.provider()).isPresent()) {
            throw new ValidationException(AuthErrorCode.PROVIDER_ALREADY_LINKED);
        }

        userSocialAccountRepository.save(
            UserSocialAccount.of(user, userInfo.provider(), userInfo.providerId()));
        log.info("소셜 계정 연결 완료: userId={}, provider={}", userId, userInfo.provider());
    }

    @Transactional
    public void unlinkSocialAccount(Long userId, OAuthProvider provider) {
        User user = getById(userId);
        if (user.getProvider() == provider) {
            throw new ValidationException(AuthErrorCode.CANNOT_UNLINK_PRIMARY);
        }
        UserSocialAccount account = userSocialAccountRepository.findByUser_IdAndProvider(userId, provider)
            .orElseThrow(() -> new NotFoundException(AuthErrorCode.SOCIAL_ACCOUNT_NOT_LINKED));
        userSocialAccountRepository.delete(account);
        log.info("소셜 계정 연결 해제: userId={}, provider={}", userId, provider);
    }

    @Transactional(readOnly = true)
    public List<LinkedProviderResponse> getLinkedProviders(Long userId) {
        User user = getById(userId);
        List<LinkedProviderResponse> result = new ArrayList<>();
        result.add(toResponse(user.getProvider(), true));
        userSocialAccountRepository.findAllByUser_Id(userId)
            .forEach(account -> result.add(toResponse(account.getProvider(), false)));
        return result;
    }

    private LinkedProviderResponse toResponse(OAuthProvider provider, boolean primary) {
        return new LinkedProviderResponse(provider.name().toLowerCase(Locale.ROOT), provider.getLabel(), primary);
    }

    @Transactional(readOnly = true)
    public User getById(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(UserErrorCode.NOT_FOUND_USER));
    }
}
