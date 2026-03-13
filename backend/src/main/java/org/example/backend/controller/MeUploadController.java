package org.example.backend.controller;

import lombok.RequiredArgsConstructor;
import org.example.backend.common.auth.AuthUtils;
import org.example.backend.common.response.Result;
import org.example.backend.model.vo.UploadImageVO;
import org.example.backend.service.core.upload.UploadService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/me/upload")
@RequiredArgsConstructor
public class MeUploadController {

    private final UploadService uploadService;

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadImageVO> uploadImage(@RequestParam("bizType") String bizType,
                                             @RequestParam("file") MultipartFile file,
                                             Authentication authentication) {
        Long userId = AuthUtils.requireLoginUserId(authentication);
        UploadImageVO uploadImageVO = uploadService.uploadImage(userId, bizType, file);
        return Result.success("Image uploaded successfully", uploadImageVO);
    }
}
