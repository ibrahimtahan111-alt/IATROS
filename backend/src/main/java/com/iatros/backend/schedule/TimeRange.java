package com.iatros.backend.schedule;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Half-open local time range {@code [start, end)} within one day. */
public record TimeRange(LocalTime start, LocalTime end) {

	public TimeRange {
		if (!end.isAfter(start)) {
			throw new IllegalArgumentException("end must be after start");
		}
	}

	public boolean overlaps(TimeRange other) {
		return start.isBefore(other.end) && other.start.isBefore(end);
	}

	public boolean contains(LocalTime from, LocalTime to) {
		return !from.isBefore(start) && !to.isAfter(end);
	}

	/** Sorted, with overlapping and touching ranges merged. */
	public static List<TimeRange> union(List<TimeRange> ranges) {
		List<TimeRange> sorted = ranges.stream().sorted(Comparator.comparing(TimeRange::start)).toList();
		List<TimeRange> merged = new ArrayList<>();
		for (TimeRange range : sorted) {
			TimeRange last = merged.isEmpty() ? null : merged.getLast();
			if (last != null && !range.start.isAfter(last.end)) {
				if (range.end.isAfter(last.end)) {
					merged.set(merged.size() - 1, new TimeRange(last.start, range.end));
				}
			}
			else {
				merged.add(range);
			}
		}
		return merged;
	}

	/** {@code ranges} (already merged) minus every range in {@code removed}. */
	public static List<TimeRange> subtract(List<TimeRange> ranges, List<TimeRange> removed) {
		List<TimeRange> result = ranges;
		for (TimeRange cut : removed) {
			List<TimeRange> next = new ArrayList<>();
			for (TimeRange range : result) {
				if (!range.overlaps(cut)) {
					next.add(range);
					continue;
				}
				if (range.start.isBefore(cut.start)) {
					next.add(new TimeRange(range.start, cut.start));
				}
				if (cut.end.isBefore(range.end)) {
					next.add(new TimeRange(cut.end, range.end));
				}
			}
			result = next;
		}
		return result;
	}

}
