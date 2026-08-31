package com.reconciliation.engine.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A clean, stable API-facing paginated response shape, decoupled from
 * Spring Data's {@link Page} (whose JSON serialization has changed across
 * Spring Data versions and exposes internal paging concepts we don't want
 * to commit to as part of our API contract). Generic so later phases
 * (risk flags, reconciliation results) can reuse it without duplicating
 * this shape.
 */
public class PagedResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean first;
    private final boolean last;

    public PagedResponse(List<T> content, int page, int size, long totalElements, int totalPages,
                          boolean first, boolean last) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.first = first;
        this.last = last;
    }

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    public List<T> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean isFirst() {
        return first;
    }

    public boolean isLast() {
        return last;
    }
}
