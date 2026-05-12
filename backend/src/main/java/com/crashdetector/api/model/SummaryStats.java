package com.crashdetector.api.model;

public record SummaryStats(
    int crashCount,
    int reboundCount,
    int averageRecoveryDays
) {
}

