package com.rehome.rehome.component;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HikariPoolDiagnosticsTest {
    @TempDir Path directory;
    @Test void disabledByDefaultWithoutRequiringAnyDatasource() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(HikariPoolDiagnostics.class);
            context.refresh();
            assertThat(context.getBeansOfType(HikariPoolDiagnostics.class)).isEmpty();
        }
    }

    @Test void enabledSamplerReportsWaitersWithoutBorrowingDatabaseConnection() throws Exception {
        var source = mock(HikariDataSource.class);
        var pool = mock(HikariPoolMXBean.class);
        when(source.getHikariPoolMXBean()).thenReturn(pool);
        when(source.getMaximumPoolSize()).thenReturn(10);
        when(pool.getActiveConnections()).thenReturn(10);
        when(pool.getIdleConnections()).thenReturn(0);
        when(pool.getTotalConnections()).thenReturn(10);
        when(pool.getThreadsAwaitingConnection()).thenReturn(42);
        Logger logger = (Logger) LoggerFactory.getLogger(HikariPoolDiagnostics.class);
        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("diagnostics",
                    Map.of("app.performance.diagnostics.enabled", "true",
                            "app.performance.diagnostics.output", directory.resolve("pool.csv").toString())));
            context.registerBean(DataSource.class, () -> source);
            context.register(HikariPoolDiagnostics.class);
            context.refresh();
            context.getBean(HikariPoolDiagnostics.class).sample();
            context.getBean(HikariPoolDiagnostics.class).sample();
            assertThat(logs.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
                    .contains("PERF_POOL timestamp=", "active=10 idle=0 total=10 waiting=42 max=10"));
            verify(source, never()).getConnection();
            var records = Files.readAllLines(directory.resolve("pool.csv"));
            assertThat(records).hasSize(3);
            assertThat(records.get(0)).isEqualTo("timestamp_ms,java_pid,active,idle,total,waiting,max");
            assertThat(records.get(1)).endsWith("," + ProcessHandle.current().pid() + ",10,0,10,42,10");
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    @Test void unavailableDatasourceDoesNotInterruptScheduledWork() throws Exception {
        DataSource source = mock(DataSource.class);
        when(source.isWrapperFor(HikariDataSource.class)).thenThrow(new java.sql.SQLException("unavailable"));
        assertThatCode(() -> new HikariPoolDiagnostics(source, directory.resolve("pool.csv").toString()).sample())
                .doesNotThrowAnyException();
        verify(source, never()).getConnection();
    }

    @Test void unwritableCsvDoesNotInterruptPoolSampling() throws Exception {
        var source = mock(HikariDataSource.class);
        when(source.getHikariPoolMXBean()).thenReturn(mock(HikariPoolMXBean.class));
        // A directory cannot be appended as a CSV file.
        var diagnostics = new HikariPoolDiagnostics(source, directory.toString());
        assertThatCode(diagnostics::sample).doesNotThrowAnyException();
        assertThatCode(diagnostics::sample).doesNotThrowAnyException();
        verify(source, never()).getConnection();
    }
}
