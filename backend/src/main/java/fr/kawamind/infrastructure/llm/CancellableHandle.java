package fr.kawamind.infrastructure.llm;

import dev.langchain4j.model.chat.response.StreamingHandle;
import java.util.concurrent.atomic.AtomicBoolean;

/** Arrêt d'un flux demandé par son consommateur, lu par les adaptateurs en flux. */
final class CancellableHandle implements StreamingHandle {

    private final AtomicBoolean cancelled = new AtomicBoolean();

    @Override
    public void cancel() {
        cancelled.set(true);
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }
}
