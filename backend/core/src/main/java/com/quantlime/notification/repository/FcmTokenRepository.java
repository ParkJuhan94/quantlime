package com.quantlime.notification.repository;

import com.quantlime.notification.domain.FcmToken;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    Optional<FcmToken> findByToken(String token);

    List<FcmToken> findAllByUser_Id(Long userId);

    List<FcmToken> findAllByUser_IdIn(Collection<Long> userIds);

    // 무효 토큰(FCM UNREGISTERED) 정리용 파생 delete 쿼리 - 호출측
    // (FcmTokenService.deleteToken)에 @Transactional 필요.
    void deleteByToken(String token);
}
