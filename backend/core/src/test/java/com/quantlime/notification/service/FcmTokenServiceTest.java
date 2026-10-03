package com.quantlime.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.notification.domain.FcmToken;
import com.quantlime.notification.implement.FcmTokenAppender;
import com.quantlime.notification.implement.FcmTokenReader;
import com.quantlime.notification.repository.FcmTokenRepository;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.service.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FcmTokenServiceTest {

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @Mock
    private UserService userService;

    private FcmTokenService service;

    @BeforeEach
    void setUp() {
        service = new FcmTokenService(
            new FcmTokenReader(fcmTokenRepository), new FcmTokenAppender(fcmTokenRepository), userService);
    }

    @Test
    @DisplayName("[처음 보는 토큰이면 새 row로 저장한다]")
    void registerToken_newToken_saves() {
        // given
        User user = UserFixture.createUser();
        given(userService.getById(1L)).willReturn(user);
        given(fcmTokenRepository.findByToken("tok")).willReturn(Optional.empty());

        // when
        service.registerToken(1L, "tok", "Chrome");

        // then
        ArgumentCaptor<FcmToken> captor = ArgumentCaptor.forClass(FcmToken.class);
        verify(fcmTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getToken()).isEqualTo("tok");
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getDeviceInfo()).isEqualTo("Chrome");
    }

    @Test
    @DisplayName("[이미 있는 토큰이 다른 계정으로 재등록되면 insert 대신 소유자만 교체한다(unique 제약 회피)]")
    void registerToken_existingToken_reassignsOwner() {
        // given
        User oldOwner = UserFixture.createUser(OAuthProvider.GOOGLE, "old");
        User newOwner = UserFixture.createUser(OAuthProvider.GOOGLE, "new");
        FcmToken existing = FcmToken.of(oldOwner, "tok", "Chrome");
        given(userService.getById(2L)).willReturn(newOwner);
        given(fcmTokenRepository.findByToken("tok")).willReturn(Optional.of(existing));

        // when
        service.registerToken(2L, "tok", "Chrome");

        // then
        assertThat(existing.getUser()).isSameAs(newOwner);
        verify(fcmTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("[토큰 삭제/조회는 저장소에 위임한다]")
    void deleteAndGet_delegate() {
        given(fcmTokenRepository.findAllByUser_Id(1L)).willReturn(List.of());
        given(fcmTokenRepository.findAllByUser_IdIn(List.of(1L, 2L))).willReturn(List.of());

        service.deleteToken("tok");

        verify(fcmTokenRepository).deleteByToken("tok");
        assertThat(service.getTokensForUser(1L)).isEmpty();
        assertThat(service.getTokensForUsers(List.of(1L, 2L))).isEmpty();
    }
}
