package dev.specwriter.application.conversation;

import dev.specwriter.domain.conversation.Citations;
import dev.specwriter.domain.conversation.ConversationMemory;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.MessageRole;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.knowledge.KnowledgeBudget;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ConversationLlm;
import dev.specwriter.domain.port.ConversationLlm.Exchange;
import dev.specwriter.domain.port.ConversationRepository;
import dev.specwriter.domain.session.Language;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.jboss.logging.Logger;

/**
 * Mémoire transmise à l'agent (010) : le résumé des échanges anciens, puis les échanges récents en
 * entier, du plus récent au plus ancien tant qu'ils tiennent dans la part de l'historique. Quand des
 * échanges sortent de cette fenêtre sans être couverts par le résumé, l'IA les résume avec lui ; le
 * compactage va jusqu'à ne laisser à la fenêtre que la moitié de sa part, pour ne pas recommencer à
 * chaque tour. Les citations {@code [n]} des réponses passées deviennent le titre de leur source.
 * Un compactage en échec ne bloque rien : la fenêtre seule est transmise, signalée tronquée.
 */
public class ConversationHistory {

    private static final Logger LOG = Logger.getLogger(ConversationHistory.class);
    /** Un message très long est coupé dans ce qui est donné à résumer. */
    static final int MAX_MESSAGE_TO_COMPACT = 3_000;
    /** Au plus autant d'appels pour rattraper un long retard (première fois, conversation ancienne). */
    static final int MAX_ROUNDS = 3;

    /** Avancement du compactage, montré à l'écran. */
    public enum Phase {
        STARTED, DONE, FAILED
    }

    /** {@code truncated} : des échanges anciens ne sont transmis ni en entier ni dans le résumé. */
    public record Prepared(String memory, List<Exchange> exchanges, boolean truncated) {
    }

    private record Line(int ordinal, Exchange exchange) {

        int size() {
            return exchange.text().length();
        }
    }

    private record Window(List<Line> kept, List<Line> dropped) {
    }

    private final ConversationRepository conversations;
    private final ConversationLlm llm;
    private final ClockPort clock;

    public ConversationHistory(ConversationRepository conversations, ConversationLlm llm, ClockPort clock) {
        this.conversations = conversations;
        this.llm = llm;
        this.clock = clock;
    }

    /** Historique des messages qui précèdent {@code before} (numéro d'ordre). */
    public Prepared prepare(UUID conversationId, List<ConversationMessage> messages, int before, Language language,
            KnowledgeBudget budget, Consumer<Phase> phases) {
        List<Line> lines = messages.stream().filter(m -> m.ordinal() < before && !m.text().isBlank())
                .map(m -> new Line(m.ordinal(), new Exchange(m.role(), m.role() == MessageRole.AGENT
                        ? Citations.inline(m.text(), conversations.sources(m.id())) : m.text())))
                .toList();
        Optional<ConversationMemory> memory = conversations.memory(conversationId);
        String summary = memory.map(ConversationMemory::summary).orElse("");
        int covered = memory.map(ConversationMemory::coveredUntil).orElse(0);
        int part = budget.conversationHistory();
        Window window = window(lines, covered, part - summary.length());
        if (window.dropped().isEmpty()) {
            return new Prepared(summary, exchanges(window.kept()), false);
        }
        phases.accept(Phase.STARTED);
        long start = System.nanoTime();
        try {
            List<Line> toCompact = new ArrayList<>(lines.stream().filter(l -> l.ordinal() > covered).toList());
            toCompact.removeAll(window(lines, covered, part / 2).kept());
            String next = summary;
            int until = covered;
            for (int round = 0; round < MAX_ROUNDS && !toCompact.isEmpty(); round++) {
                List<Line> chunk = chunk(toCompact, part * 2);
                next = llm.compact(new ConversationLlm.CompactRequest(language, next, chunk.stream()
                        .map(l -> new Exchange(l.exchange().role(), cut(l.exchange().text()))).toList()));
                until = chunk.getLast().ordinal();
                toCompact.removeAll(chunk);
            }
            ConversationMemory saved = new ConversationMemory(next, until, clock.now());
            conversations.saveMemory(conversationId, saved);
            phases.accept(Phase.DONE);
            LOG.infof("Conversation : échanges résumés jusqu'au message %d en %d ms", until,
                    (System.nanoTime() - start) / 1_000_000);
            Window after = window(lines, until, part - saved.summary().length());
            return new Prepared(saved.summary(), exchanges(after.kept()), !after.dropped().isEmpty());
        } catch (DomainException | IllegalArgumentException | IllegalStateException e) {
            LOG.warnf("Conversation : compactage en échec (%s), fenêtre seule transmise", e.getClass().getSimpleName());
            phases.accept(Phase.FAILED);
            return new Prepared(summary, exchanges(window.kept()), true);
        }
    }

    /** Messages récents non résumés qui tiennent dans {@code room}, du plus récent au plus ancien. */
    private static Window window(List<Line> lines, int covered, int room) {
        List<Line> kept = new ArrayList<>();
        List<Line> dropped = new ArrayList<>();
        int left = room;
        boolean full = false;
        for (int i = lines.size() - 1; i >= 0; i--) {
            Line line = lines.get(i);
            if (line.ordinal() <= covered) {
                break;
            }
            if (full || line.size() > left) {
                full = true;
                dropped.add(line);
                continue;
            }
            left -= line.size();
            kept.add(line);
        }
        Collections.reverse(kept);
        Collections.reverse(dropped);
        return new Window(kept, dropped);
    }

    /** Premiers messages à résumer, dans {@code room} caractères ; au moins un. */
    private static List<Line> chunk(List<Line> lines, int room) {
        List<Line> out = new ArrayList<>();
        int used = 0;
        for (Line line : lines) {
            int size = Math.min(line.size(), MAX_MESSAGE_TO_COMPACT);
            if (!out.isEmpty() && used + size > room) {
                break;
            }
            out.add(line);
            used += size;
        }
        return out;
    }

    private static String cut(String text) {
        return text.length() <= MAX_MESSAGE_TO_COMPACT ? text : text.substring(0, MAX_MESSAGE_TO_COMPACT) + "…";
    }

    private static List<Exchange> exchanges(List<Line> lines) {
        return lines.stream().map(Line::exchange).toList();
    }
}
