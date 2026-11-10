package dev.specwriter.infrastructure.llm;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rejeu en flux (E2E, dev) : le champ {@code text} de la fixture, rendu par groupes de quelques mots. */
final class ReplayConversationStream implements ConversationStream {

    private static final Pattern CHUNK = Pattern.compile("(?:\\S+\\s*){1,4}|\\s+");

    private final ReplayChatModel replay;

    ReplayConversationStream(ReplayChatModel replay) {
        this.replay = replay;
    }

    @Override
    public String stream(String system, String user, Consumer<String> deltas, BooleanSupplier cancelled) {
        String text = replay.text(system, user);
        StringBuilder sent = new StringBuilder();
        Matcher m = CHUNK.matcher(text);
        while (m.find() && !cancelled.getAsBoolean()) {
            sent.append(m.group());
            deltas.accept(m.group());
        }
        return sent.toString();
    }
}
