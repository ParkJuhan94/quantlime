package com.quantlime.videofeed.domain.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TelegramFilterConfigConverter implements AttributeConverter<TelegramFilterConfig, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(TelegramFilterConfig attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("텔레그램 필터 설정 직렬화에 실패했습니다.", e);
        }
    }

    @Override
    public TelegramFilterConfig convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, TelegramFilterConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("텔레그램 필터 설정 역직렬화에 실패했습니다.", e);
        }
    }
}
