package org.example.backend.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> records;
    private long total;
    private int page;
    private int size;
    private int totalPages;
    private boolean hasNext;

    public static <T> PageResult<T> from(Page<T> pageData) {
        return PageResult.<T>builder()
                .records(pageData.getContent())
                .total(pageData.getTotalElements())
                .page(pageData.getNumber())
                .size(pageData.getSize())
                .totalPages(pageData.getTotalPages())
                .hasNext(pageData.hasNext())
                .build();
    }
}

