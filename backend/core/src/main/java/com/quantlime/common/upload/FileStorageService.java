package com.quantlime.common.upload;

import com.quantlime.common.exception.ValidationException;
import com.quantlime.common.upload.exception.UploadErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 업로드를 로컬 디스크에 저장한다(단일 EC2 배포 구조와 맞물려 - 별도
 * 오브젝트 스토리지 계정/버킷 없이 백엔드 컨테이너가 마운트한 디렉터리에
 * 그대로 쓰고, 같은 디렉터리를 정적 리소스로 서빙한다. WebConfig의
 * addResourceHandlers 참고). 향후 트래픽이 늘면 S3 같은 오브젝트 스토리지로
 * 옮기는 걸 검토할 수 있지만, 지금 규모에선 새 클라우드 자격증명·버킷
 * 없이 바로 되는 이 방식이 더 단순하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
        Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final String PUBLIC_PATH_PREFIX = "/uploads/";

    private final FileStorageProperties properties;

    public String storeImage(MultipartFile file) {
        validate(file);
        String filename = UUID.randomUUID() + extractExtension(file.getOriginalFilename());
        Path uploadDir = Path.of(properties.getDir());
        Path target = uploadDir.resolve(filename);
        try {
            Files.createDirectories(uploadDir);
            file.transferTo(target);
        } catch (IOException e) {
            // 클라이언트 입력 문제가 아니라 서버 쪽 저장 실패라 400이 아닌
            // 500으로 흘러가야 한다 - GlobalExceptionHandler의 catch-all에 위임.
            throw new RuntimeException("이미지 저장에 실패했습니다.", e);
        }
        log.info("이미지 업로드 완료: filename={}, size={}bytes", filename, file.getSize());
        return PUBLIC_PATH_PREFIX + filename;
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ValidationException(UploadErrorCode.EMPTY_FILE);
        }
        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new ValidationException(UploadErrorCode.FILE_TOO_LARGE);
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new ValidationException(UploadErrorCode.INVALID_FILE_TYPE);
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.'));
    }
}
