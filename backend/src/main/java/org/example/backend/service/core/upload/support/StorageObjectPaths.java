package org.example.backend.service.core.upload.support;

import lombok.RequiredArgsConstructor;
import org.example.backend.common.constant.AppConstants.UploadBizType;
import org.example.backend.config.AppStorageProperties;
import org.example.backend.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StorageObjectPaths {

    private final AppStorageProperties appStorageProperties;

    public String generateObjectKey(Long userId, String bizType, String extension) {
        String bizPrefix = switch (bizType) {
            case UploadBizType.ARTICLE_COVER -> "article/cover";
            case UploadBizType.ARTICLE_CONTENT -> "article/content";
            case UploadBizType.USER_AVATAR -> "user/avatar";
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
                extension.toLowerCase(Locale.ROOT));
    }

    public String buildPublicUrl(String objectKey) {
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
