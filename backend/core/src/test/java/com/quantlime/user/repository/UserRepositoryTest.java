package com.quantlime.user.repository;

import com.quantlime.support.DataJpaTestSupport;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class UserRepositoryTest extends DataJpaTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("[provider와 providerId로 사용자를 조회한다]")
    void findByProviderAndProviderId_existingUser_returnsUser() {
        // given
        User user = UserFixture.createUser(OAuthProvider.GOOGLE, "google-id-1");
        userRepository.save(user);

        // when
        Optional<User> found = userRepository.findByProviderAndProviderId(
            OAuthProvider.GOOGLE, "google-id-1");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getNickname()).isEqualTo(user.getNickname());
    }

    @Test
    @DisplayName("[존재하지 않는 provider/providerId 조합은 빈 값을 반환한다]")
    void findByProviderAndProviderId_notFound_returnsEmpty() {
        // when
        Optional<User> found = userRepository.findByProviderAndProviderId(
            OAuthProvider.KAKAO, "no-such-id");

        // then
        assertThat(found).isEmpty();
    }
}
