package org.example.backend.service.core.upload.impl;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.example.backend.config.AppStorageProperties;
import org.example.backend.exception.BizException;
import org.example.backend.model.vo.UploadImageVO;
import org.example.backend.service.core.upload.UploadService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    private static final String BIZ_ARTICLE_COVER = "article_cover";
    private static final String BIZ_ARTICLE_CONTENT = "article_content";
    private static final String BIZ_USER_AVATAR = "user_avatar";

    private final AppStorageProperties appStorageProperties;
    private final MinioClient minioClient;

    @PostConstruct
    public void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(appStorageProperties.getBucket()).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(appStorageProperties.getBucket()).build()
                );
            }
        } catch (Exception ex) {
            throw new BizException("STORAGE_INIT_FAILED", "Failed to initialize storage bucket");
        }
    }

    @Override
    public UploadImageVO uploadImage(Long userId, String bizType, MultipartFile file) {
        validatePositive("userId", userId);
        String normalizedBizType = normalizeBizType(bizType);
        validateFile(file);

        String contentType = file.getContentType();
        String extension = resolveExtension(file, contentType);
        String objectKey = generateObjectKey(userId, normalizedBizType, extension);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(appStorageProperties.getBucket())
                    .object(objectKey)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            throw new BizException("UPLOAD_FAILED", "Failed to upload image");
        }

        return UploadImageVO.builder()
                .bizType(normalizedBizType)
                .key(objectKey)
                .url(buildPublicUrl(objectKey))
                .size(file.getSize())
                .contentType(contentType)
                .build();
    }

    private void validatePositive(String fieldName, Long value) {
        if (value == null || value <= 0) {
            throw new BizException("INVALID_PARAM", fieldName + " must be a positive number");
        }
    }

    private String normalizeBizType(String bizType) {
        if (!StringUtils.hasText(bizType)) {
            throw new BizException("INVALID_PARAM", "bizType cannot be blank");
        }
        String normalized = bizType.trim().toLowerCase(Locale.ROOT);
        if (!BIZ_ARTICLE_COVER.equals(normalized)
                && !BIZ_ARTICLE_CONTENT.equals(normalized)
                && !BIZ_USER_AVATAR.equals(normalized)) {
            throw new BizException("INVALID_PARAM", "Unsupported bizType");
        }
        return normalized;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("INVALID_PARAM", "file cannot be empty");
        }
        if (file.getSize() > appStorageProperties.getMaxImageSizeBytes()) {
            throw new BizException("FILE_TOO_LARGE", "Image size exceeds max limit");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)) {
            throw new BizException("INVALID_PARAM", "file content type is missing");
        }

        Set<String> allowedTypes = appStorageProperties.getAllowedContentTypes().stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!allowedTypes.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BizException("INVALID_PARAM", "Unsupported image content type");
        }
    }

    private String resolveExtension(MultipartFile file, String contentType) {
        Set<String> allowedExtensions = appStorageProperties.getAllowedExtensions().stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (StringUtils.hasText(extension)) {
            String normalized = extension.toLowerCase(Locale.ROOT);
            if (allowedExtensions.contains(normalized)) {
                return normalized;
            }
        }

        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> throw new BizException("INVALID_PARAM", "Unsupported image extension");
        };
    }

    private String generateObjectKey(Long userId, String bizType, String extension) {
        String bizPrefix = switch (bizType) {
            case BIZ_ARTICLE_COVER -> "article/cover";
            case BIZ_ARTICLE_CONTENT -> "article/content";
            case BIZ_USER_AVATAR -> "user/avatar";
            default -> throw new BizException("INVALID_PARAM", "Unsupported bizType");
        };
        LocalDate today = LocalDate.now();
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return String.format("%s/%d/%02d/%02d/%d/%s.%s",
                bizPrefix,
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                userId,
                uuid,
                extension);
    }

    private String buildPublicUrl(String objectKey) {
        String publicBaseUrl = appStorageProperties.getPublicBaseUrl();
        if (StringUtils.hasText(publicBaseUrl)) {
            return trimRightSlash(publicBaseUrl) + "/" + objectKey;
        }
        return trimRightSlash(appStorageProperties.getEndpoint())
                + "/"
                + appStorageProperties.getBucket()
                + "/"
                + objectKey;
    }

    private String trimRightSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
