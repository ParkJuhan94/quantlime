package com.quantlime.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.infra.oauth.dto.OAuthUserInfo;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService service;

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
}
