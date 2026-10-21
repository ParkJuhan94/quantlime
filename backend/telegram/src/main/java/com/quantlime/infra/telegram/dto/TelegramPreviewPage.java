package com.quantlime.infra.telegram.dto;

import java.util.List;
import org.springframework.lang.Nullable;

public record TelegramPreviewPage(
    @Nullable String channelTitle,
    @Nullable String channelPhotoUrl,
    List<TelegramPreviewMessage> messages
) {
}
