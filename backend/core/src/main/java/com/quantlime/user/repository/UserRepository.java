package com.quantlime.user.repository;

import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(OAuthProvider provider, String providerId);
}
