package org.example.backend.service.core.upload;

import org.example.backend.model.vo.UploadImageVO;
import org.springframework.web.multipart.MultipartFile;

public interface UploadService {

    UploadImageVO uploadImage(Long userId, String bizType, MultipartFile file);
}
