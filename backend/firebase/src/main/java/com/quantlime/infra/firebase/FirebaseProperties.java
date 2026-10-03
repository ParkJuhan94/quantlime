package com.quantlime.infra.firebase;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "fcm")
public class FirebaseProperties {

    private final String serviceAccountPath;
}
