package com.quantlime.user.repository;

import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    // 관리자 공지 브로드캐스트(AdminNotificationController) 대상 전체 조회용 -
    // 엔티티 전체를 안 불러오고 id만 뽑는다.
    @Query("select u.id from User u")
    List<Long> findAllIds();
}
