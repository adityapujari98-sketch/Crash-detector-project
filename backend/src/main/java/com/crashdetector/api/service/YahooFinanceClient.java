package com.crashdetector.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class YahooFinanceClient {

    private static final String BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Candle> fetchHistory(String ticker, String period) throws IOException, InterruptedException {
        String encodedTicker = URLEncoder.encode(ticker, StandardCharsets.UTF_8);
        String url = BASE_URL + encodedTicker + "?interval=1d&range=" + period + "&includePrePost=false&events=div%2Csplits";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "CrashDetector/1.0")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Failed to load market data for " + ticker);
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            throw new IOException("No market data for " + ticker);
        }

        JsonNode first = result.get(0);
        JsonNode timestamps = first.path("timestamp");
        JsonNode quote = first.path("indicators").path("quote");
        if (!quote.isArray() || quote.isEmpty()) {
            throw new IOException("Missing quote data for " + ticker);
        }

        JsonNode quoteBlock = quote.get(0);
        JsonNode opens = quoteBlock.path("open");
        JsonNode highs = quoteBlock.path("high");
        JsonNode lows = quoteBlock.path("low");
        JsonNode closes = quoteBlock.path("close");

        List<Candle> candles = new ArrayList<>();
        int size = timestamps.size();
        for (int index = 0; index < size; index++) {
            if (opens.get(index).isNull() || highs.get(index).isNull() || lows.get(index).isNull() || closes.get(index).isNull()) {
                continue;
            }

            LocalDate date = Instant.ofEpochSecond(timestamps.get(index).asLong())
                .atZone(ZoneOffset.UTC)
                .toLocalDate();

            candles.add(new Candle(
                date,
                opens.get(index).asDouble(),
                highs.get(index).asDouble(),
                lows.get(index).asDouble(),
                closes.get(index).asDouble()
            ));
        }

        return candles;
    }

    public record Candle(
        LocalDate date,
        double open,
        double high,
        double low,
        double close
    ) {
    }
}

