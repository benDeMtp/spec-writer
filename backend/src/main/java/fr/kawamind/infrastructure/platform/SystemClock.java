package fr.kawamind.infrastructure.platform;

import fr.kawamind.domain.port.ClockPort;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;

@ApplicationScoped
public class SystemClock implements ClockPort {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
