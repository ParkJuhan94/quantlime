package com.quantlime.user.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.infra.oauth.dto.OAuthUserInfo;
import com.quantlime.user.domain.User;
import com.quantlime.user.exception.UserErrorCode;
import com.quantlime.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User findOrCreate(OAuthUserInfo userInfo) {
        return userRepository.findByProviderAndProviderId(
                userInfo.provider(), userInfo.providerId())
            .map(user -> {
                user.updateProfile(userInfo.email(), userInfo.nickname(),
                    userInfo.profileImageUrl());
                return user;
            })
            .orElseGet(() -> {
                User user = User.of(userInfo.email(), userInfo.nickname(),
                    userInfo.profileImageUrl(), userInfo.provider(), userInfo.providerId());
                User saved = userRepository.save(user);
                log.info("신규 사용자 가입 완료: userId={}, provider={}",
                    saved.getId(), saved.getProvider());
                return saved;
            });
    }

    @Transactional(readOnly = true)
    public User getById(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(UserErrorCode.NOT_FOUND_USER));
    }
}
