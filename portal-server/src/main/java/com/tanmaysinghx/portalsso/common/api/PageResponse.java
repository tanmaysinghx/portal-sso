package com.tanmaysinghx.portalsso.common.api;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * The wire shape for every paged admin list.
 *
 * <p>Hand-rolled rather than serialising Spring's {@code Page}, whose JSON changes between versions
 * and exposes internals the console has no use for. Generalised from the audit log's envelope once
 * a third endpoint needed the same thing.
 *
 * @param content the rows for this page
 * @param page zero-based page index
 * @param size the requested page size, after clamping
 * @param totalElements rows matching the filters, across all pages
 * @param totalPages pages at the current size
 */
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> source, Function<E, T> mapper) {
        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages());
    }

    /** For the paths that map the rows themselves — e.g. after a second query fetches associations. */
    public static <E, T> PageResponse<T> of(Page<E> source, List<T> content) {
        return new PageResponse<>(
                content, source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages());
    }
}
