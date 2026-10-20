package com.iatros.backend.shared.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.iatros.backend.shared.error.BadRequestException;

/**
 * Builds validated page requests from {@code ?page=&size=&sort=field,dir} parameters (api.md §26–27).
 * Sizes above the maximum are clamped; unknown sort fields are rejected with INVALID_SORT.
 * The entity ID is always appended as a final tie-breaker so pages are stable.
 */
public final class PageRequests {

	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 100;

	private PageRequests() {
	}

	public static Pageable of(Integer page, Integer size, List<String> sort, SortWhitelist whitelist) {
		Sort order = sort == null || sort.isEmpty() ? whitelist.defaultSort() : parse(sort, whitelist);
		if (order.getOrderFor("id") == null) {
			order = order.and(Sort.by(Sort.Direction.ASC, "id"));
		}
		return unsorted(page, size).withSort(order);
	}

	/** For queries with a fixed order of their own, such as native feeds. */
	public static PageRequest unsorted(Integer page, Integer size) {
		int pageNumber = page == null ? 0 : page;
		if (pageNumber < 0) {
			throw new BadRequestException("INVALID_PAGE", "page must not be negative");
		}
		int pageSize = size == null ? DEFAULT_SIZE : size;
		if (pageSize < 1) {
			throw new BadRequestException("INVALID_PAGE_SIZE", "size must be positive");
		}
		return PageRequest.of(pageNumber, Math.min(pageSize, MAX_SIZE));
	}

	private static Sort parse(List<String> sortParameters, SortWhitelist whitelist) {
		List<Sort.Order> orders = new ArrayList<>();
		for (String parameter : rejoinDirections(sortParameters)) {
			String[] parts = parameter.split(",", -1);
			if (parts.length > 2) {
				throw invalidSort();
			}
			String property = whitelist.propertyFor(parts[0].trim());
			if (property == null) {
				throw invalidSort();
			}
			Sort.Direction direction = parts.length == 2 ? parseDirection(parts[1]) : Sort.Direction.ASC;
			orders.add(new Sort.Order(direction, property));
		}
		return Sort.by(orders);
	}

	/**
	 * Spring binds a single {@code sort=field,dir} value to a list by splitting on the comma, producing
	 * {@code [field, dir]}. Re-attach each bare direction to the field before it.
	 */
	private static List<String> rejoinDirections(List<String> tokens) {
		List<String> parameters = new ArrayList<>();
		for (String token : tokens) {
			boolean direction = isDirection(token);
			if (direction && !parameters.isEmpty() && !parameters.getLast().contains(",")) {
				parameters.set(parameters.size() - 1, parameters.getLast() + "," + token);
			}
			else {
				parameters.add(token);
			}
		}
		return parameters;
	}

	private static boolean isDirection(String value) {
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return normalized.equals("asc") || normalized.equals("desc");
	}

	private static Sort.Direction parseDirection(String value) {
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "asc" -> Sort.Direction.ASC;
			case "desc" -> Sort.Direction.DESC;
			default -> throw invalidSort();
		};
	}

	private static BadRequestException invalidSort() {
		return new BadRequestException("INVALID_SORT", "Unsupported sort parameter");
	}

}
