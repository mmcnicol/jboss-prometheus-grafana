package io.github.jpg.portal;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory store of {@link Discharge} records. No database in Phase 0 — the
 * point is a realistic request path, not persistence. A small artificial delay
 * on read and write stands in for datasource latency.
 */
@ApplicationScoped
public class DischargeStore {

    private final List<Discharge> records = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    @PostConstruct
    void seed() {
        for (int i = 1; i <= 8; i++) {
            Discharge d = new Discharge();
            d.setPatientReference(String.format("PT-%04d", 1000 + i));
            d.setWard("Ward " + (char) ('A' + (i % 4)));
            d.setAdmissionDate(LocalDate.now().minusDays(10L + i));
            d.setDischargeDate(LocalDate.now().minusDays(i));
            d.setSummary("Seeded record " + i + " — routine admission and discharge.");
            d.setFollowUp("GP review in 2 weeks.");
            d.setCreatedBy("seed");
            save(d);
        }
    }

    public List<Discharge> findAll() {
        pause(15);
        return new ArrayList<>(records);
    }

    public Discharge save(Discharge d) {
        pause(25);
        if (d.getId() == null) {
            d.setId(sequence.incrementAndGet());
            d.setCreatedAt(LocalDateTime.now());
            records.add(d);
        }
        return d;
    }

    public int count() {
        return records.size();
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
