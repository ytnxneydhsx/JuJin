package org.example.backend.common.page;

import org.example.backend.exception.BizException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

public final class PageUtils {

    private static final String INVALID_PAGINATION_MESSAGE =
            "Invalid pagination parameters: page must be >= 0 and size must be > 0";

    private PageUtils() {
    }

    public static Pageable pageable(int page, int size) {
        validate(page, size);
        return PageRequest.of(page, size);
    }

    public static int offset(Pageable pageable) {
        return Math.toIntExact(pageable.getOffset());
    }

    public static <T> Page<T> page(List<T> records, Pageable pageable, long total) {
        return new PageImpl<>(records, pageable, total);
    }

    private static void validate(int page, int size) {
        if (page < 0 || size <= 0) {
            throw new BizException("INVALID_PARAM", INVALID_PAGINATION_MESSAGE);
        }
    }
}
