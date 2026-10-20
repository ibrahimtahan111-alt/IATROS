package com.iatros.backend.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Names of the fields an update changes, for audit metadata. Only names are kept, never values, so personal and
 * clinical content stays out of the audit trail (security.md §23).
 */
public final class ChangedFields {

	private final List<String> names = new ArrayList<>();

	public ChangedFields compare(String field, Object current, Object requested) {
		if (!Objects.equals(current, requested)) {
			names.add(field);
		}
		return this;
	}

	public List<String> names() {
		return List.copyOf(names);
	}

}
