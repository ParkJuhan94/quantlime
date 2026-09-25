package com.quantlime.notification.service;

import com.quantlime.notification.domain.FcmToken;
import com.quantlime.notification.repository.FcmTokenRepository;
import com.quantlime.user.domain.User;
import com.quantlime.user.service.UserService;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FcmTokenService {

    private final FcmTokenRepository fcmTokenRepository;
    private final UserService userService;

    // 같은 토큰 문자열이 다른 계정으로 재등록되면(같은 브라우저에서 계정
    // 전환 등) 새 row를 만들지 않고 소유자만 교체한다 - token 컬럼이
    // unique라 그대로 insert하면 제약 위반이 난다.
    @Transactional
    public void registerToken(Long userId, String token, String deviceInfo) {
        User user = userService.getById(userId);
        fcmTokenRepository.findByToken(token)
            .ifPresentOrElse(
                existing -> existing.reassignTo(user),
                () -> fcmTokenRepository.save(FcmToken.of(user, token, deviceInfo)));
    }

    @Transactional
    public void deleteToken(String token) {
        fcmTokenRepository.deleteByToken(token);
    }

    @Transactional(readOnly = true)
    public List<FcmToken> getTokensForUser(Long userId) {
        return fcmTokenRepository.findAllByUser_Id(userId);
    }

    @Transactional(readOnly = true)
    public List<FcmToken> getTokensForUsers(Collection<Long> userIds) {
        return fcmTokenRepository.findAllByUser_IdIn(userIds);
    }
}
