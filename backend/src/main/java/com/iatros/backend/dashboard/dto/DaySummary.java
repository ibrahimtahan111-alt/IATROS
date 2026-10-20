package com.iatros.backend.dashboard.dto;

/**
 * Today's appointments by progress (api.md §22). {@code appointments} and {@code patients} exclude cancelled
 * appointments; {@code remaining} are those still SCHEDULED or CONFIRMED.
 */
public record DaySummary(long appointments, long patients, long remaining, long waiting, long inConsultation,
		long completed, long noShows, long cancelled) {
}
