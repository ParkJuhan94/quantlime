package com.quantlime.user.implement;

import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 사용자 저장을 감싸는 구현 레이어. 트랜잭션 경계는 호출하는 서비스가 소유한다. */
@Component
@RequiredArgsConstructor
public class UserAppender {

    private final UserRepository userRepository;

    public User save(User user) {
        return userRepository.save(user);
    }
}
