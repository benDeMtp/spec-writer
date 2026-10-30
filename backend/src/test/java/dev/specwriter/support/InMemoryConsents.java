package dev.specwriter.support;

import dev.specwriter.domain.consent.ConsentRecord;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.port.ConsentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryConsents implements ConsentRepository {
    private final List<ConsentRecord> history = new ArrayList<>();

    @Override
    public void record(ConsentRecord record) {
        history.add(record);
    }

    @Override
    public Optional<ConsentRecord> latest(ConsentService service) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).service() == service) {
                return Optional.of(history.get(i));
            }
        }
        return Optional.empty();
    }

    public List<ConsentRecord> history() {
        return List.copyOf(history);
    }
}
