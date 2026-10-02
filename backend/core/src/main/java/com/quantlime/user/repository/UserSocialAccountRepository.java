package com.quantlime.user.repository;

import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.UserSocialAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSocialAccountRepository extends JpaRepository<UserSocialAccount, Long> {

    // 로그인 시 연결된 소셜 계정으로 사용자를 찾는다 - user를 함께 가져와 N+1을 피한다.
    @Query("select a from UserSocialAccount a join fetch a.user where a.provider = :provider "
        + "and a.providerId = :providerId")
    Optional<UserSocialAccount> findByProviderAndProviderId(
        @Param("provider") OAuthProvider provider, @Param("providerId") String providerId);

    List<UserSocialAccount> findAllByUser_Id(Long userId);

    Optional<UserSocialAccount> findByUser_IdAndProvider(Long userId, OAuthProvider provider);
}
