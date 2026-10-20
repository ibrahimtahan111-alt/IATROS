package com.iatros.backend.shared.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Standard paginated response wrapper (api.md §26, decisions.md D-006). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
		List<T> content = page.getContent().stream().<T>map(mapper).toList();
		return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

}
