package com.quantlime.common.config;

import com.quantlime.auth.resolver.LoginUserArgumentResolver;
import com.quantlime.auth.resolver.OptionalLoginUserArgumentResolver;
import com.quantlime.common.upload.FileStorageProperties;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final OptionalLoginUserArgumentResolver optionalLoginUserArgumentResolver;
    private final FileStorageProperties fileStorageProperties;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
        resolvers.add(optionalLoginUserArgumentResolver);
    }

    // 업로드된 이미지를 별도 오브젝트 스토리지 없이 백엔드가 직접 정적
    // 리소스로 서빙한다(FileStorageService 참고) - /uploads/{파일명}으로
    // 요청하면 로컬 디스크의 실제 파일을 그대로 반환한다.
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Path.of(fileStorageProperties.getDir()).toAbsolutePath() + "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
