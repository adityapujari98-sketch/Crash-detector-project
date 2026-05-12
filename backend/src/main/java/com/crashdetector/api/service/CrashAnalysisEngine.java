package com.crashdetector.api.service;

import com.crashdetector.api.model.CandlePoint;
import com.crashdetector.api.model.CrashWindow;
import com.crashdetector.api.model.SummaryStats;
import com.crashdetector.api.model.TickerAnalysis;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CrashAnalysisEngine {

    public TickerAnalysis analyze(String ticker, List<YahooFinanceClient.Candle> candles, double threshold) {
        List<Double> closes = candles.stream().map(YahooFinanceClient.Candle::close).toList();
        int[] labels = labelCrashesAndRebounds(closes, threshold, 20);

        List<CandlePoint> points = new ArrayList<>();
        for (int index = 0; index < candles.size(); index++) {
            YahooFinanceClient.Candle candle = candles.get(index);
            points.add(new CandlePoint(
                candle.date(),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                labels[index]
            ));
        }

        List<CrashWindow> windows = findCrashWindows(candles, labels);
        SummaryStats summary = buildSummary(windows);
        return new TickerAnalysis(ticker, summary, points, windows);
    }

    private SummaryStats buildSummary(List<CrashWindow> windows) {
        int crashCount = windows.size();
        int reboundCount = (int) windows.stream().filter(window -> window.reboundDate() != null).count();
        double average = windows.stream()
            .filter(window -> window.daysToRebound() != null)
            .mapToInt(CrashWindow::daysToRebound)
            .average()
            .orElse(0);

        return new SummaryStats(crashCount, reboundCount, (int) Math.round(average));
    }

    private List<CrashWindow> findCrashWindows(List<YahooFinanceClient.Candle> candles, int[] labels) {
        List<CrashWindow> windows = new ArrayList<>();
        Map<LocalDate, YahooFinanceClient.Candle> candleByDate = new HashMap<>();
        for (YahooFinanceClient.Candle candle : candles) {
            candleByDate.put(candle.date(), candle);
        }

        LocalDate crashDate = null;
        for (int index = 0; index < labels.length; index++) {
            LocalDate currentDate = candles.get(index).date();
            if (labels[index] == -1) {
                crashDate = currentDate;
            } else if (labels[index] == 1 && crashDate != null) {
                LocalDate reboundDate = currentDate;
                windows.add(buildCompletedWindow(crashDate, reboundDate, candles, candleByDate));
                crashDate = null;
            }
        }

        if (crashDate != null) {
            windows.add(new CrashWindow(crashDate, null, null, null, null, "Ongoing / No rebound yet"));
        }

        return windows;
    }

    private CrashWindow buildCompletedWindow(
        LocalDate crashDate,
        LocalDate reboundDate,
        List<YahooFinanceClient.Candle> candles,
        Map<LocalDate, YahooFinanceClient.Candle> candleByDate
    ) {
        YahooFinanceClient.Candle crashCandle = candleByDate.get(crashDate);
        YahooFinanceClient.Candle reboundCandle = candleByDate.get(reboundDate);

        double trough = candles.stream()
            .filter(candle -> !candle.date().isBefore(crashDate) && !candle.date().isAfter(reboundDate))
            .mapToDouble(YahooFinanceClient.Candle::close)
            .min()
            .orElse(crashCandle.close());

        double maxDrawdownPct = ((trough - crashCandle.close()) / crashCandle.close()) * 100.0;
        double recoveryPct = trough == 0 ? 0 : ((reboundCandle.close() - trough) / trough) * 100.0;
        int daysToRebound = (int) ChronoUnit.DAYS.between(crashDate, reboundDate);

        return new CrashWindow(
            crashDate,
            reboundDate,
            daysToRebound,
            round(maxDrawdownPct),
            round(recoveryPct),
            "Recovered"
        );
    }

    private int[] labelCrashesAndRebounds(List<Double> prices, double w, int volatilityWindow) {
        int totalPoints = prices.size();
        int[] labels = new int[totalPoints];
        if (totalPoints == 0) {
            return labels;
        }

        double[] volatility = calculateVolatility(prices, volatilityWindow);
        int baselineRange = Math.max(50, totalPoints / 10);
        double baselineVolatility = meanIgnoringNaN(volatility, Math.min(baselineRange, volatility.length));
        double volatilityThreshold = Double.isNaN(baselineVolatility) ? Double.NaN : baselineVolatility * 1.5;

        double localPeak = prices.get(0);
        double localTrough = prices.get(0);
        int trend = 0;
        int startIndex = 0;

        for (int index = 0; index < totalPoints; index++) {
            double price = prices.get(index);
            if (price > localPeak * (1 + w)) {
                localPeak = price;
                trend = 1;
                startIndex = index;
                labels[index] = -1;
                break;
            }
            if (price < localTrough * (1 - w)) {
                localTrough = price;
                trend = -1;
                startIndex = index;
                labels[index] = 1;
                break;
            }
        }

        for (int index = startIndex; index < totalPoints; index++) {
            double price = prices.get(index);
            if (trend == 1) {
                if (price > localPeak) {
                    localPeak = price;
                }
                if (price < localPeak * (1 - w)) {
                    localTrough = price;
                    labels[index] = -1;
                    trend = -1;
                }
            } else if (trend == -1) {
                if (price < localTrough) {
                    localTrough = price;
                }
                if (!Double.isNaN(volatility[index]) && !Double.isNaN(volatilityThreshold) && volatility[index] < volatilityThreshold) {
                    localPeak = price;
                    labels[index] = 1;
                    trend = 1;
                }
            }
        }

        return labels;
    }

    private double[] calculateVolatility(List<Double> prices, int window) {
        int size = prices.size();
        double[] logReturns = new double[size];
        double[] volatility = new double[size];

        logReturns[0] = Double.NaN;
        volatility[0] = Double.NaN;
        for (int index = 1; index < size; index++) {
            double previous = prices.get(index - 1);
            double current = prices.get(index);
            logReturns[index] = previous <= 0 || current <= 0 ? Double.NaN : Math.log(current / previous);
        }

        for (int index = 0; index < size; index++) {
            if (index + 1 < window) {
                volatility[index] = Double.NaN;
                continue;
            }

            double mean = 0;
            int count = 0;
            for (int offset = index - window + 1; offset <= index; offset++) {
                if (!Double.isNaN(logReturns[offset])) {
                    mean += logReturns[offset];
                    count++;
                }
            }

            if (count == 0) {
                volatility[index] = Double.NaN;
                continue;
            }

            mean /= count;
            double variance = 0;
            for (int offset = index - window + 1; offset <= index; offset++) {
                if (!Double.isNaN(logReturns[offset])) {
                    double diff = logReturns[offset] - mean;
                    variance += diff * diff;
                }
            }

            volatility[index] = Math.sqrt(variance / count);
        }

        return volatility;
    }

    private double meanIgnoringNaN(double[] values, int length) {
        double sum = 0;
        int count = 0;
        for (int index = 0; index < length; index++) {
            if (!Double.isNaN(values[index])) {
                sum += values[index];
                count++;
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}

