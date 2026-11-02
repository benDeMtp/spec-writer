package dev.specwriter.support;

import dev.specwriter.domain.port.IdGenerator;
import java.util.UUID;

/** Identifiants de test prévisibles : 00000000-0000-0000-0000-000000000001, …002, … */
public class SequentialIdGenerator implements IdGenerator {

    private long next = 1;

    @Override
    public UUID next() {
        return new UUID(0, next++);
    }
}
