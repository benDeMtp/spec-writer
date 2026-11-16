package fr.kawamind.infrastructure.llm;

import dev.langchain4j.guardrail.GuardrailException;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Appel d'un AI Service par un adaptateur de port (014, research R3, R4) : le délai de l'appel est
 * fixé pour l'adaptateur Copilot, et les erreurs du framework deviennent des erreurs métier. Une
 * sortie toujours non conforme après les reprises du garde-fou donne {@code LLM_INVALID_OUTPUT}.
 */
final class AiCalls {

    private AiCalls() {
    }

    static <T> T call(Duration timeout, Supplier<T> call) {
        try {
            return ScopedValue.where(CallTimeout.CURRENT, timeout).call(call::get);
        } catch (RuntimeException e) {
            throw domain(e);
        }
    }

    /** Erreur métier d'une erreur levée pendant un appel : celle du modèle, ou une sortie refusée. */
    static DomainException domain(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof DomainException d) {
                return d;
            }
            if (t instanceof GuardrailException
                    || t instanceof io.quarkiverse.langchain4j.runtime.aiservice.GuardrailException) {
                return new DomainException(ErrorCode.LLM_INVALID_OUTPUT);
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return new DomainException(ErrorCode.LLM_UNAVAILABLE, ErrorCode.LLM_UNAVAILABLE.defaultMessage(),
                fr.kawamind.infrastructure.logging.SafeThrowable.of(error));
    }
}
