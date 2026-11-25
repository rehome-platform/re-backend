package com.rehome.rehome.component;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Optional load-test telemetry. Reads pool counters without borrowing a JDBC connection. */
@Component
@Slf4j
@ConditionalOnProperty(name = "app.performance.diagnostics.enabled", havingValue = "true")
public class HikariPoolDiagnostics {
    private final DataSource dataSource;
    private final Path output;
    private boolean unavailableLogged;
    private boolean outputFailureLogged;

    public HikariPoolDiagnostics(DataSource dataSource,
            @Value("${app.performance.diagnostics.output:}") String output) {
        this.dataSource = dataSource;
        this.output = (output == null || output.isBlank()
                ? Path.of("performance", "results", "backend-pool-" + ProcessHandle.current().pid() + ".csv")
                : Path.of(output)).toAbsolutePath().normalize();
    }

    @Scheduled(initialDelayString = "${app.performance.diagnostics.interval-ms:5000}",
            fixedDelayString = "${app.performance.diagnostics.interval-ms:5000}")
    public void sample() {
        try {
            HikariDataSource hikari = dataSource instanceof HikariDataSource direct ? direct
                    : dataSource.isWrapperFor(HikariDataSource.class)
                            ? dataSource.unwrap(HikariDataSource.class) : null;
            HikariPoolMXBean pool = hikari == null ? null : hikari.getHikariPoolMXBean();
            if (pool == null) {
                unavailable("pool is not initialized or is not Hikari");
                return;
            }
            long timestamp = System.currentTimeMillis();
            int active = pool.getActiveConnections();
            int idle = pool.getIdleConnections();
            int total = pool.getTotalConnections();
            int waiting = pool.getThreadsAwaitingConnection();
            int maximum = hikari.getMaximumPoolSize();
            log.info("PERF_POOL timestamp={} active={} idle={} total={} waiting={} max={}",
                    timestamp, active, idle, total, waiting, maximum);
            append(timestamp, active, idle, total, waiting, maximum);
        } catch (Exception ex) {
            // Diagnostics must not stop other scheduled jobs or change application behavior.
            unavailable(ex.getClass().getSimpleName());
        }
    }

    private void append(long timestamp, int active, int idle, int total, int waiting, int maximum) {
        try {
            Files.createDirectories(output.getParent());
            boolean empty = !Files.exists(output) || Files.size(output) == 0;
            String row = timestamp + "," + ProcessHandle.current().pid() + "," + active + "," + idle
                    + "," + total + "," + waiting + "," + maximum + "\n";
            Files.writeString(output, (empty ? "timestamp_ms,java_pid,active,idle,total,waiting,max\n" : "") + row,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ex) {
            if (!outputFailureLogged) {
                log.warn("PERF_POOL CSV unavailable: {}", ex.getClass().getSimpleName());
                outputFailureLogged = true;
            }
        }
    }

    private void unavailable(String reason) {
        if (!unavailableLogged) {
            log.warn("PERF_POOL unavailable: {}", reason);
            unavailableLogged = true;
        }
    }
}
