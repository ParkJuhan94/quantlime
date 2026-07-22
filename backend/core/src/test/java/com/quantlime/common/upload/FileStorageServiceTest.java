package com.quantlime.common.upload;

import com.quantlime.common.exception.ValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties =
            new FileStorageProperties(tempDir.toString(), 1024, "http://localhost:8080");
        fileStorageService = new FileStorageService(properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        try (var files = Files.list(tempDir)) {
            files.forEach(path -> path.toFile().delete());
        }
    }

    @Test
    @DisplayName("[유효한 이미지를 업로드하면 디스크에 저장하고 /uploads/ 경로를 반환한다]")
    void storeImage_validImage_savesToDiskAndReturnsPublicPath() {
        // given
        MultipartFile file = new MockMultipartFile("image", "photo.png", "image/png", new byte[]{1, 2, 3});

        // when
        String result = fileStorageService.storeImage(file);

        // then
        assertThat(result).startsWith("/uploads/").endsWith(".png");
        String filename = result.substring("/uploads/".length());
        assertThat(tempDir.resolve(filename)).exists();
    }

    @Test
    @DisplayName("[빈 파일이면 예외를 던진다]")
    void storeImage_emptyFile_throws() {
        // given
        MultipartFile file = new MockMultipartFile("image", "empty.png", "image/png", new byte[0]);

        // when & then
        assertThatThrownBy(() -> fileStorageService.storeImage(file))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("[허용된 이미지 타입이 아니면 예외를 던진다]")
    void storeImage_invalidContentType_throws() {
        // given
        MultipartFile file = new MockMultipartFile("image", "malware.exe", "application/x-msdownload", new byte[]{1});

        // when & then
        assertThatThrownBy(() -> fileStorageService.storeImage(file))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("[설정된 최대 크기를 넘으면 예외를 던진다]")
    void storeImage_exceedsMaxSize_throws() {
        // given: 설정한 한도(1024바이트)를 넘는 파일
        MultipartFile file = new MockMultipartFile("image", "big.png", "image/png", new byte[2048]);

        // when & then
        assertThatThrownBy(() -> fileStorageService.storeImage(file))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("[원본 파일명에 확장자가 없으면 확장자 없이 저장한다]")
    void storeImage_noExtension_savesWithoutExtension() {
        // given
        MultipartFile file = new MockMultipartFile("image", "noext", "image/png", new byte[]{1});

        // when
        String result = fileStorageService.storeImage(file);

        // then
        assertThat(result).doesNotContain(".");
    }
}
