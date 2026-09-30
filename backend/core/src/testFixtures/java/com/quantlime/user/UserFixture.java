package com.quantlime.user;

import static lombok.AccessLevel.PRIVATE;

import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class UserFixture {

    public static User createUser() {
        return createUser(OAuthProvider.GOOGLE, "google-provider-id-1");
    }

    public static User createUser(OAuthProvider provider, String providerId) {
        return User.of("test@example.com", "테스트유저", null, provider, providerId);
    }
}
