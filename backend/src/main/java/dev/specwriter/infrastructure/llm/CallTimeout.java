package dev.specwriter.infrastructure.llm;

import java.time.Duration;

/**
 * Délai de l'appel au LLM en cours, fixé par {@link LangChainLlmClient} selon l'opération et lu par
 * {@link CopilotRuntime} : le {@code ChatModel} de LangChain4j n'a pas de paramètre pour le porter.
 */
public final class CallTimeout {

    /** Délai hors d'un appel de {@link LangChainLlmClient}, celui des opérations interactives. */
    public static final Duration DEFAULT = Duration.ofSeconds(180);

    static final ScopedValue<Duration> CURRENT = ScopedValue.newInstance();

    private CallTimeout() {
    }

    public static Duration current() {
        return CURRENT.orElse(DEFAULT);
    }
}
