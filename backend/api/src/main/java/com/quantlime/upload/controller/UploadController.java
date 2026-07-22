package com.quantlime.upload.controller;

import com.quantlime.auth.resolver.LoginUser;
import com.quantlime.common.upload.FileStorageService;
import com.quantlime.common.upload.dto.UploadImageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "파일 업로드 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/uploads")
public class UploadController {

    private final FileStorageService fileStorageService;

    @PostMapping(value = "/images", consumes = "multipart/form-data")
    @Operation(
        summary = "이미지 업로드",
        description = "피드 게시글·의견 보내기 첨부용 이미지를 업로드한다(jpg/png/gif/webp, 최대 5MB, 로그인 필요)"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<UploadImageResponse> uploadImage(
        @LoginUser Long userId,
        @RequestParam("image") MultipartFile image) {
        String imageUrl = fileStorageService.storeImage(image);
        return ResponseEntity.ok(new UploadImageResponse(imageUrl));
    }
}
