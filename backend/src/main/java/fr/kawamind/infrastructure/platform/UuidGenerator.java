package fr.kawamind.infrastructure.platform;

import fr.kawamind.domain.port.IdGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class UuidGenerator implements IdGenerator {
    @Override
    public UUID next() {
        return UUID.randomUUID();
    }
}
