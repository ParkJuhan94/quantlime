package com.quantlime.infra.telegram.dto;

import java.util.List;

public record TelegramPreviewPage(
    String channelTitle,
    String channelPhotoUrl,
    List<TelegramPreviewMessage> messages
) {
}
