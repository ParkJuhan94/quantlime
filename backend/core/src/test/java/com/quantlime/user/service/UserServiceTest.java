package com.quantlime.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.auth.dto.OAuthUserInfo;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.domain.UserSocialAccount;
import com.quantlime.user.implement.UserAppender;
import com.quantlime.user.implement.UserReader;
import com.quantlime.user.implement.UserSocialAccountAppender;
import com.quantlime.user.implement.UserSocialAccountReader;
import com.quantlime.user.repository.UserRepository;
import com.quantlime.user.repository.UserSocialAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSocialAccountRepository userSocialAccountRepository;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(
            new UserReader(userRepository), new UserAppender(userRepository),
            new UserSocialAccountReader(userSocialAccountRepository),
            new UserSocialAccountAppender(userSocialAccountRepository));
    }

    private final OAuthUserInfo info =
        new OAuthUserInfo(OAuthProvider.GOOGLE, "pid-1", "new@example.com", "새닉네임", "http://img");

    @Test
    @DisplayName("[처음 로그인하는 사용자는 OAuth 정보로 신규 가입 저장한다]")
    void findOrCreate_newUser_saves() {
        // given
        given(userRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "pid-1")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        User user = service.findOrCreate(info);

        // then
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getNickname()).isEqualTo("새닉네임");
        assertThat(user.getProvider()).isEqualTo(OAuthProvider.GOOGLE);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("[기존 사용자는 새로 저장하지 않고 프로필(이메일/닉네임/사진)만 최신으로 갱신한다]")
    void findOrCreate_existingUser_updatesProfileWithoutSaving() {
        // given
        User existing = UserFixture.createUser(OAuthProvider.GOOGLE, "pid-1");
        given(userRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "pid-1"))
            .willReturn(Optional.of(existing));

        // when
        User user = service.findOrCreate(info);

        // then
        assertThat(user).isSameAs(existing);
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getNickname()).isEqualTo("새닉네임");
        assertThat(user.getProfileImageUrl()).isEqualTo("http://img");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("[getById는 없으면 NotFoundException]")
    void getById_missing_throws() {
        given(userRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(9L)).isInstanceOf(NotFoundException.class);
    }

    // --- 소셜 계정 연결 ---

    private User userWithId(long id, OAuthProvider provider, String providerId) {
        User user = UserFixture.createUser(provider, providerId);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private final OAuthUserInfo kakaoInfo =
        new OAuthUserInfo(OAuthProvider.KAKAO, "kakao-1", "k@example.com", "카카오", null);

    @Test
    @DisplayName("[가입 계정과 다른 제공자의 미사용 소셜 계정은 연결된다]")
    void linkSocialAccount_unclaimedAccount_saves() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithId(1L, OAuthProvider.GOOGLE, "g-1")));

        // when
        service.linkSocialAccount(1L, kakaoInfo);

        // then
        verify(userSocialAccountRepository).save(any(UserSocialAccount.class));
    }

    @Test
    @DisplayName("[다른 사용자의 가입 계정이면 연결을 거부한다]")
    void linkSocialAccount_ownedByOtherPrimary_throws() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithId(1L, OAuthProvider.GOOGLE, "g-1")));
        given(userRepository.findByProviderAndProviderId(OAuthProvider.KAKAO, "kakao-1"))
            .willReturn(Optional.of(userWithId(2L, OAuthProvider.KAKAO, "kakao-1")));

        // when & then
        assertThatThrownBy(() -> service.linkSocialAccount(1L, kakaoInfo))
            .isInstanceOf(ValidationException.class);
        verify(userSocialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("[다른 사용자에게 이미 연결된 계정이면 연결을 거부한다]")
    void linkSocialAccount_linkedToOtherUser_throws() {
        // given
        User other = userWithId(2L, OAuthProvider.NAVER, "n-1");
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithId(1L, OAuthProvider.GOOGLE, "g-1")));
        given(userSocialAccountRepository.findByProviderAndProviderId(OAuthProvider.KAKAO, "kakao-1"))
            .willReturn(Optional.of(UserSocialAccount.of(other, OAuthProvider.KAKAO, "kakao-1")));

        // when & then
        assertThatThrownBy(() -> service.linkSocialAccount(1L, kakaoInfo))
            .isInstanceOf(ValidationException.class);
        verify(userSocialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("[이미 내 계정에 연결된 같은 소셜 계정을 다시 연결하면 조용히 성공한다]")
    void linkSocialAccount_alreadyMine_idempotent() {
        // given
        User me = userWithId(1L, OAuthProvider.GOOGLE, "g-1");
        given(userRepository.findById(1L)).willReturn(Optional.of(me));
        given(userSocialAccountRepository.findByProviderAndProviderId(OAuthProvider.KAKAO, "kakao-1"))
            .willReturn(Optional.of(UserSocialAccount.of(me, OAuthProvider.KAKAO, "kakao-1")));

        // when
        service.linkSocialAccount(1L, kakaoInfo);

        // then
        verify(userSocialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("[가입 계정과 같은 제공자의 다른 계정은 연결할 수 없다]")
    void linkSocialAccount_sameProviderDifferentId_throws() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithId(1L, OAuthProvider.KAKAO, "kakao-other")));

        // when & then
        assertThatThrownBy(() -> service.linkSocialAccount(1L, kakaoInfo))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("[연결된 소셜 계정으로 로그인하면 연결 대상 사용자로 로그인하고 프로필은 바꾸지 않는다]")
    void findOrCreate_linkedAccount_returnsLinkedUserWithoutProfileOverwrite() {
        // given
        User owner = userWithId(1L, OAuthProvider.GOOGLE, "g-1");
        String nicknameBefore = owner.getNickname();
        given(userSocialAccountRepository.findByProviderAndProviderId(OAuthProvider.KAKAO, "kakao-1"))
            .willReturn(Optional.of(UserSocialAccount.of(owner, OAuthProvider.KAKAO, "kakao-1")));

        // when
        User result = service.findOrCreate(kakaoInfo);

        // then
        assertThat(result).isSameAs(owner);
        assertThat(result.getNickname()).isEqualTo(nicknameBefore);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("[가입에 사용한 계정은 연결 해제할 수 없다]")
    void unlinkSocialAccount_primary_throws() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithId(1L, OAuthProvider.GOOGLE, "g-1")));

        // when & then
        assertThatThrownBy(() -> service.unlinkSocialAccount(1L, OAuthProvider.GOOGLE))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("[연결되지 않은 제공자를 해제하면 NotFoundException을 던진다]")
    void unlinkSocialAccount_notLinked_throws() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithId(1L, OAuthProvider.GOOGLE, "g-1")));
        given(userSocialAccountRepository.findByUser_IdAndProvider(1L, OAuthProvider.KAKAO)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.unlinkSocialAccount(1L, OAuthProvider.KAKAO))
            .isInstanceOf(NotFoundException.class);
    }
}
