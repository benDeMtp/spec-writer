package dev.specwriter.application.conversation;

import dev.specwriter.application.conversation.ConversationSearch.Found;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.specification.SpecificationSections;
import java.util.ArrayList;
import java.util.List;

/**
 * Spécification discutée comme sources de l'agent (011, research.md R2) : une source par section,
 * {@code spec:<session>@v<n>#<ancre>}, en tête des sources. Les sections qui ne tiennent pas dans
 * leur part sont réduites à leur titre et nommées parmi les sources non comparées.
 */
final class DiscussionSources {

    private DiscussionSources() {
    }

    static void add(Converse.Sources sources, DiscussSpecification.Context context, int room) {
        List<Found> found = new ArrayList<>();
        for (SpecificationSections.Section s : SpecificationSections.fit(SpecificationSections.of(context.markdown()),
                room)) {
            String title = context.specificationName() + ", " + s.title();
            if (s.partial()) {
                sources.notCompared(title);
            } else {
                found.add(new Found(SourceKind.SPECIFICATION, ref(context, s.anchor()), title, s.text()));
            }
        }
        sources.add(found);
    }

    static String ref(DiscussSpecification.Context context, String anchor) {
        return ConversationSearch.SPEC_PREFIX + context.sessionId() + "@v" + context.version() + "#" + anchor;
    }

    /** Résultat de recherche qui vient de la spécification discutée. */
    static boolean isDiscussed(String ref, DiscussSpecification.Context context) {
        return ref.startsWith(ConversationSearch.SPEC_PREFIX + context.sessionId() + "@");
    }
}
