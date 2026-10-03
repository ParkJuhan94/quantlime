package com.quantlime.user.implement;

import com.quantlime.user.domain.UserSocialAccount;
import com.quantlime.user.repository.UserSocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 연결된 소셜 계정 저장·삭제를 감싸는 구현 레이어. 트랜잭션 경계는 호출하는 서비스가 소유한다. */
@Component
@RequiredArgsConstructor
public class UserSocialAccountAppender {

    private final UserSocialAccountRepository userSocialAccountRepository;

    public UserSocialAccount save(UserSocialAccount account) {
        return userSocialAccountRepository.save(account);
    }

    public void delete(UserSocialAccount account) {
        userSocialAccountRepository.delete(account);
    }
}
