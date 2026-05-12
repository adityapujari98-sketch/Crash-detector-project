package com.crashdetector.api.model;

import java.time.LocalDate;

public record CrashWindow(
    LocalDate crashDate,
    LocalDate reboundDate,
    Integer daysToRebound,
    Double maxDrawdownPct,
    Double recoveryPct,
    String status
) {
}

