package com.crashdetector.api.service;

import com.crashdetector.api.model.AnalysisResponse;
import com.crashdetector.api.model.TickerAnalysis;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CrashDetectorService {

    private final YahooFinanceClient yahooFinanceClient;
    private final CrashAnalysisEngine crashAnalysisEngine;

    public CrashDetectorService(YahooFinanceClient yahooFinanceClient, CrashAnalysisEngine crashAnalysisEngine) {
        this.yahooFinanceClient = yahooFinanceClient;
        this.crashAnalysisEngine = crashAnalysisEngine;
    }

    public AnalysisResponse analyze(List<String> tickers, String period, double threshold) {
        List<TickerAnalysis> analyses = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (String ticker : tickers) {
            try {
                List<YahooFinanceClient.Candle> candles = yahooFinanceClient.fetchHistory(ticker, period);
                if (candles.size() < 50) {
                    errors.add(ticker);
                    continue;
                }
                analyses.add(crashAnalysisEngine.analyze(ticker, candles, threshold));
            } catch (Exception exception) {
                errors.add(ticker);
            }
        }

        return new AnalysisResponse(analyses, errors);
    }
}

