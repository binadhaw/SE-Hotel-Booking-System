package com.Reservation.Hotel.service;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Proposal: "Administrator can ... monitor system performance" (NFR performance & reliability).
 * Keeps the timings of the most recent requests in memory (recorded by RequestMetricsFilter) and turns them
 * into the figures on the admin dashboard: response times, error rate, uptime, database and memory status.
 */
@Service
public class SystemHealthService {

    /** How many recent requests the figures are based on. */
    static final int WINDOW = 2000;
    /** Above these the platform is shown as "Degraded". */
    static final long SLOW_P95_MS = 1000;
    static final double HIGH_ERROR_RATE_PCT = 2.0;

    private record Sample(String page, int status, long millis) {}

    public record PageStat(String page, long requests, long avgMillis) {}

    public record Health(String status, String uptime, long requestsServed, long recentRequests,
                         long avgMillis, long p95Millis, double errorRatePct,
                         boolean databaseUp, long databaseMillis, long heapUsedMb, long heapMaxMb,
                         List<PageStat> slowestPages) {
        public boolean isHealthy() { return "Healthy".equals(status); }
    }

    private final DataSource dataSource;
    private final Instant startedAt = Instant.now();
    private final Deque<Sample> recent = new ArrayDeque<>();
    private long requestsServed;

    public SystemHealthService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Called once per request; ids in the path are folded so /hotels/5 and /hotels/9 count as one page. */
    public synchronized void record(String method, String uri, int status, long millis) {
        requestsServed++;
        recent.addLast(new Sample(method + " " + pageOf(uri), status, millis));
        if (recent.size() > WINDOW) recent.removeFirst();
    }

    static String pageOf(String uri) {
        return uri.replaceAll("/TXN-[A-Z0-9]+", "/{transaction}")
                .replaceAll("/\\d+(?=/|$)", "/{id}");
    }

    public Health snapshot() {
        List<Sample> samples;
        long served;
        synchronized (this) {
            samples = new ArrayList<>(recent);
            served = requestsServed;
        }

        List<Long> times = samples.stream().map(Sample::millis).sorted().toList();
        long avg = times.isEmpty() ? 0 : Math.round(times.stream().mapToLong(Long::longValue).average().orElse(0));
        long p95 = times.isEmpty() ? 0 : times.get((int) Math.ceil(times.size() * 0.95) - 1);
        long errors = samples.stream().filter(s -> s.status() >= 500).count();
        double errorRate = samples.isEmpty() ? 0 : Math.round(errors * 1000.0 / samples.size()) / 10.0;

        Map<String, List<Sample>> byPage = samples.stream().collect(Collectors.groupingBy(Sample::page));
        List<PageStat> slowest = byPage.entrySet().stream()
                .map(e -> new PageStat(e.getKey(), e.getValue().size(),
                        Math.round(e.getValue().stream().mapToLong(Sample::millis).average().orElse(0))))
                .sorted(Comparator.comparingLong(PageStat::avgMillis).reversed())
                .limit(5).toList();

        long dbStart = System.nanoTime();
        boolean dbUp = databaseResponds();
        long dbMillis = (System.nanoTime() - dbStart) / 1_000_000;

        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);

        String status = !dbUp ? "Down"
                : (p95 > SLOW_P95_MS || errorRate > HIGH_ERROR_RATE_PCT) ? "Degraded" : "Healthy";
        return new Health(status, uptime(Duration.between(startedAt, Instant.now())), served, samples.size(),
                avg, p95, errorRate, dbUp, dbMillis, usedMb, maxMb, slowest);
    }

    private boolean databaseResponds() {
        try (Connection c = dataSource.getConnection()) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    static String uptime(Duration d) {
        if (d.toDays() > 0) return d.toDays() + "d " + d.toHoursPart() + "h";
        if (d.toHours() > 0) return d.toHours() + "h " + d.toMinutesPart() + "m";
        return d.toMinutes() + "m " + d.toSecondsPart() + "s";
    }
}
