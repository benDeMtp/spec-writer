package dev.specwriter.infrastructure.platform;

import dev.specwriter.domain.port.ConversationRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

/**
 * Au démarrage, une réponse de l'agent restée en cours (outil fermé pendant l'écriture) devient
 * incomplète : son texte est gardé et elle peut être réessayée (010, research.md R6).
 */
@Singleton
public class ConversationStartup {

    private static final Logger LOG = Logger.getLogger(ConversationStartup.class);

    private final ConversationRepository conversations;

    @Inject
    public ConversationStartup(ConversationRepository conversations) {
        this.conversations = conversations;
    }

    void onStart(@Observes StartupEvent event) {
        int interrupted = conversations.interruptRunning();
        if (interrupted > 0) {
            LOG.infof("Conversation : %d réponse(s) interrompue(s) à la fermeture, marquée(s) incomplète(s)", interrupted);
        }
    }
}
