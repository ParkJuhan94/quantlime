package com.quantlime.notification.implement;

import com.quantlime.notification.domain.FcmToken;
import com.quantlime.notification.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** FCM 토큰 저장·삭제를 감싸는 구현 레이어. 파생 delete 쿼리라 호출하는 서비스가 @Transactional 경계를 소유한다. */
@Component
@RequiredArgsConstructor
public class FcmTokenAppender {

    private final FcmTokenRepository fcmTokenRepository;

    public FcmToken save(FcmToken token) {
        return fcmTokenRepository.save(token);
    }

    public void deleteByToken(String token) {
        fcmTokenRepository.deleteByToken(token);
    }
}
