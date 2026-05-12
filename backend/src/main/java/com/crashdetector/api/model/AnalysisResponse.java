package com.crashdetector.api.model;

import java.util.List;

public record AnalysisResponse(
    List<TickerAnalysis> tickers,
    List<String> errors
) {
}

