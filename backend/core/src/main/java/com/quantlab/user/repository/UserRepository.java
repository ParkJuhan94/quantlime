package com.quantlab.user.repository;

import com.quantlab.user.domain.OAuthProvider;
import com.quantlab.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(OAuthProvider provider, String providerId);
}
