package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class RevenueTrendResponse {
    private final String granularity;
    private final LocalDate from;
    private final LocalDate to;
    private final long total;
    private final List<Point> points;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Point {
        private final String label;
        private final LocalDate periodStart;
        private final LocalDate periodEnd;
        private final long amount;
    }
}
