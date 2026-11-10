package dev.specwriter.infrastructure.llm;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Réponse en texte libre rédigée en flux (010, research.md R2) : Copilot, ou le rejeu. */
interface ConversationStream {

    String stream(String system, String user, Consumer<String> deltas, BooleanSupplier cancelled);
}
