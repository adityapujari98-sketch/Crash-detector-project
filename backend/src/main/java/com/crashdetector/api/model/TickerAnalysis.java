package com.crashdetector.api.model;

import java.util.List;

public record TickerAnalysis(
    String ticker,
    SummaryStats summary,
    List<CandlePoint> candles,
    List<CrashWindow> windows
) {
}

