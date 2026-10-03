package com.quantlime.notification.implement;

import com.quantlime.notification.domain.FcmToken;
import com.quantlime.notification.repository.FcmTokenRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** FCM 토큰 조회를 감싸는 구현 레이어. 메서드 이름은 Repository와 같다. */
@Component
@RequiredArgsConstructor
public class FcmTokenReader {

    private final FcmTokenRepository fcmTokenRepository;

    public Optional<FcmToken> findByToken(String token) {
        return fcmTokenRepository.findByToken(token);
    }

    public List<FcmToken> findAllByUser_Id(Long userId) {
        return fcmTokenRepository.findAllByUser_Id(userId);
    }

    public List<FcmToken> findAllByUser_IdIn(Collection<Long> userIds) {
        return fcmTokenRepository.findAllByUser_IdIn(userIds);
    }
}
