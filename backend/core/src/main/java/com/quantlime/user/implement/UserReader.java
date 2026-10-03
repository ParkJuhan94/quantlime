package com.quantlime.user.implement;

import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 사용자 조회를 감싸는 구현 레이어(Implementation). 메서드 이름은 Repository와 같다. */
@Component
@RequiredArgsConstructor
public class UserReader {

    private final UserRepository userRepository;

    public Optional<User> findByProviderAndProviderId(OAuthProvider provider, String providerId) {
        return userRepository.findByProviderAndProviderId(provider, providerId);
    }

    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }

    public List<User> findAllById(Collection<Long> userIds) {
        return userRepository.findAllById(userIds);
    }

    public List<Long> findAllIds() {
        return userRepository.findAllIds();
    }
}
