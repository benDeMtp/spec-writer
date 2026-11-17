package fr.kawamind.domain.session;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** T047 : une feature implémentée est verrouillée (FR-026a). */
class SpecificationLockTest {

    @Test
    void seuleImplementeeEstVerrouillee() {
        for (ImplementationStatus s : ImplementationStatus.values()) {
            Implementation i = new Implementation(s, s == ImplementationStatus.IMPLEMENTED ? Instant.EPOCH : null, null);
            if (s == ImplementationStatus.IMPLEMENTED) {
                assertThatThrownBy(() -> SpecificationLock.requireEditable(i)).isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.SPECIFICATION_LOCKED));
            } else {
                assertThatCode(() -> SpecificationLock.requireEditable(i)).doesNotThrowAnyException();
            }
        }
    }
}
