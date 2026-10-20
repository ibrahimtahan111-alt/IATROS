package com.iatros.backend.shared.web;

import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Sort;

/**
 * Sortable API fields of one endpoint, mapped to entity properties, plus the default order.
 * Anything not listed is rejected, so clients cannot sort on internal or unindexed columns.
 */
public record SortWhitelist(Map<String, String> propertiesByApiField, Sort defaultSort) {

	public SortWhitelist {
		propertiesByApiField = Map.copyOf(propertiesByApiField);
		Objects.requireNonNull(defaultSort, "defaultSort");
	}

	public static SortWhitelist of(Sort defaultSort, Map<String, String> propertiesByApiField) {
		return new SortWhitelist(propertiesByApiField, defaultSort);
	}

	String propertyFor(String apiField) {
		return propertiesByApiField.get(apiField);
	}

}
