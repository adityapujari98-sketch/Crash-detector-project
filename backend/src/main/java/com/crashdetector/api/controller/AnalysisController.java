package com.crashdetector.api.controller;

import com.crashdetector.api.model.AnalysisResponse;
import com.crashdetector.api.service.CrashDetectorService;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final CrashDetectorService crashDetectorService;

    public AnalysisController(CrashDetectorService crashDetectorService) {
        this.crashDetectorService = crashDetectorService;
    }

    @GetMapping("/analyze")
    public AnalysisResponse analyze(
        @RequestParam String tickers,
        @RequestParam(defaultValue = "10y") String period,
        @RequestParam(defaultValue = "0.15") double threshold
    ) {
        List<String> tickerList = List.of(tickers.split(","))
            .stream()
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(String::toUpperCase)
            .toList();

        return crashDetectorService.analyze(tickerList, period, threshold);
    }
}

