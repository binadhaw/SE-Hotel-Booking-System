package com.Reservation.Hotel;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The admin "System health" figures (proposal: administrators monitor system performance). */
class SystemHealthServiceTest {

    private static SystemHealthService service(boolean databaseUp) throws SQLException {
        DataSource ds = mock(DataSource.class);
        if (databaseUp) {
            Connection c = mock(Connection.class);
            when(c.isValid(2)).thenReturn(true);
            when(ds.getConnection()).thenReturn(c);
        } else {
            when(ds.getConnection()).thenThrow(new SQLException("down"));
        }
        return new SystemHealthService(ds);
    }

    @Test
    void responseTimesAndErrorRateComeFromRecordedRequests() throws Exception {
        SystemHealthService s = service(true);
        for (int i = 1; i <= 100; i++) s.record("GET", "/hotels", 200, i);   // 1..100 ms
        s.record("GET", "/hotels/7", 500, 10);

        SystemHealthService.Health h = s.snapshot();
        assertEquals(101, h.requestsServed());
        assertEquals(95, h.p95Millis(), "95th percentile");
        assertEquals(1.0, h.errorRatePct(), 0.01);
        assertTrue(h.databaseUp());
        assertEquals("Healthy", h.status());
    }

    @Test
    void idsAreFoldedSoOnePageIsCountedOnce() throws Exception {
        SystemHealthService s = service(true);
        s.record("GET", "/hotels/5", 200, 40);
        s.record("GET", "/hotels/9", 200, 60);
        s.record("GET", "/payments/TXN-AB12", 200, 5);

        var pages = s.snapshot().slowestPages();
        assertEquals("GET /hotels/{id}", pages.get(0).page());
        assertEquals(2, pages.get(0).requests());
        assertEquals(50, pages.get(0).avgMillis());
        assertTrue(pages.stream().anyMatch(p -> p.page().equals("GET /payments/{transaction}")));
    }

    @Test
    void slowPagesOrServerErrorsMarkThePlatformDegraded() throws Exception {
        SystemHealthService slow = service(true);
        for (int i = 0; i < 20; i++) slow.record("GET", "/hotels", 200, 3000);
        assertEquals("Degraded", slow.snapshot().status());

        SystemHealthService failing = service(true);
        for (int i = 0; i < 10; i++) failing.record("POST", "/bookings", 500, 20);
        assertEquals("Degraded", failing.snapshot().status());
    }

    @Test
    void unreachableDatabaseIsReportedAsDown() throws Exception {
        SystemHealthService.Health h = service(false).snapshot();
        assertFalse(h.databaseUp());
        assertEquals("Down", h.status());
    }
}
