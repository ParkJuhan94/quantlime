package com.quantlime.videofeed.domain.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ChannelFilterConfigConverter implements AttributeConverter<ChannelFilterConfig, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(ChannelFilterConfig attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("채널 필터 설정 직렬화에 실패했습니다.", e);
        }
    }

    @Override
    public ChannelFilterConfig convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, ChannelFilterConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("채널 필터 설정 역직렬화에 실패했습니다.", e);
        }
    }
}
