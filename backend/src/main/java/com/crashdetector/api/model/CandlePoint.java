package com.crashdetector.api.model;

import java.time.LocalDate;

public record CandlePoint(
    LocalDate date,
    double open,
    double high,
    double low,
    double close,
    int label
) {
}

