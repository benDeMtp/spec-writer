package dev.specwriter.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

/** Chaîne d'exceptions journalisable sans contenu (FR-025 de 001) : classes, marqueurs et piles. */
class SafeThrowableTest {

    @Test
    void classesEtPilesGardeesMessagesRetires() {
        RuntimeException root = new RuntimeException("Le besoin confidentiel : réserver la salle B12");
        ExecutionException wrapper = new ExecutionException("échec de « réserver la salle B12 »", root);

        SafeThrowable safe = SafeThrowable.of(wrapper);

        assertThat(safe.getMessage()).isEqualTo("java.util.concurrent.ExecutionException");
        assertThat(safe.getStackTrace()).isEqualTo(wrapper.getStackTrace());
        assertThat(safe.getCause()).isInstanceOf(SafeThrowable.class);
        assertThat(safe.getCause().getMessage()).isEqualTo("java.lang.RuntimeException");
        assertThat(safe.getCause().getStackTrace()).isEqualTo(root.getStackTrace());
        assertThat(safe.getCause().getCause()).isNull();
        assertThat(safe.toString()).isEqualTo("java.util.concurrent.ExecutionException");
    }

    @Test
    void marqueursTechniquesReconnusRepris() {
        assertThat(SafeThrowable.of(new RuntimeException("Request failed with status code 503 for prompt « secret »"))
                .getMessage()).isEqualTo("java.lang.RuntimeException [HTTP 503]");
        assertThat(SafeThrowable.of(new RuntimeException("connect ECONNREFUSED 127.0.0.1:8118")).getMessage())
                .isEqualTo("java.lang.RuntimeException [ECONNREFUSED]");
        assertThat(SafeThrowable.of(new RuntimeException("unable to get local issuer certificate "
                + "(UNABLE_TO_GET_ISSUER_CERT_LOCALLY)")).getMessage())
                .isEqualTo("java.lang.RuntimeException [UNABLE_TO_GET_ISSUER_CERT_LOCALLY, certificat]");
        assertThat(SafeThrowable.of(new RuntimeException("CLI process exited with code 1")).getMessage())
                .isEqualTo("java.lang.RuntimeException [code de sortie 1]");
        assertThat(SafeThrowable.of(new RuntimeException("prompt token count of 140000 exceeds the limit of 128000"))
                .getMessage()).isEqualTo("java.lang.RuntimeException [limite de jetons]");
        assertThat(SafeThrowable.of(new RuntimeException("Request timed out")).getMessage())
                .isEqualTo("java.lang.RuntimeException [délai dépassé]");
        assertThat(SafeThrowable.of(new TimeoutException()).getMessage()).isEqualTo("java.util.concurrent.TimeoutException");
    }

    @Test
    void chaineBorneeEtCycleSansBoucle() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        SafeThrowable safe = SafeThrowable.of(b);
        int depth = 0;
        for (Throwable t = safe; t != null; t = t.getCause()) {
            depth++;
        }
        assertThat(depth).isLessThanOrEqualTo(SafeThrowable.MAX_DEPTH);
    }
}
