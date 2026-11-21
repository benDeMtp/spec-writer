package fr.kawamind.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.application.conversation.TurnEvents;
import fr.kawamind.application.knowledge.Indexations;
import fr.kawamind.application.knowledge.SynchronizeKnowledge;
import fr.kawamind.application.lifecycle.LongWorkTracker;
import fr.kawamind.application.lifecycle.RunningWork;
import fr.kawamind.application.lifecycle.WorkKind;
import fr.kawamind.application.settings.ManageBrowser;
import fr.kawamind.domain.port.BrowserPort;
import fr.kawamind.domain.settings.Browser;
import fr.kawamind.infrastructure.web.LaunchTokenService;
import fr.kawamind.infrastructure.web.TabOpener;
import fr.kawamind.support.FakeEmbedding;
import fr.kawamind.support.InMemoryConversations;
import fr.kawamind.support.InMemoryKnowledgeIndex;
import fr.kawamind.support.InMemoryKnowledgeSources;
import fr.kawamind.support.InMemorySettings;
import fr.kawamind.support.JiraWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** T040 : Dock et ⌘Q sous macOS, derrière une abstraction de Desktop (019, R6). */
class MacDesktopIntegrationTest {

    @Test
    void neSAppliqueQuASousMacAvecEcranVerrouEtNavigateur() {
        assertThat(MacDesktopIntegration.applies("Mac OS X", false, true, true)).isTrue();
        assertThat(MacDesktopIntegration.applies("Linux", false, true, true)).isFalse();
        assertThat(MacDesktopIntegration.applies("Mac OS X", true, true, true)).isFalse();
        assertThat(MacDesktopIntegration.applies("Mac OS X", false, false, true)).isFalse();
        assertThat(MacDesktopIntegration.applies("Mac OS X", false, true, false)).isFalse();
    }

    @Test
    void confirmationNommeLesTravaux() {
        String text = MacDesktopIntegration.confirmation(List.of(
                new RunningWork.Item(WorkKind.INDEXATION, "Aurora", null),
                new RunningWork.Item(WorkKind.AGENT_REPLY, "Aurora", "Facturation des avoirs")));

        assertThat(text).contains("Indexation du projet Aurora : elle s'arrête")
                .contains("Réponse de l'agent dans Facturation des avoirs : le texte déjà reçu est gardé");
    }

    @Test
    void libellesDeChaqueNature() {
        assertThat(MacDesktopIntegration.label(new RunningWork.Item(WorkKind.REVISION, null, "Avoirs")))
                .isEqualTo("Proposition de version dans Avoirs : elle est perdue, à relancer.");
        assertThat(MacDesktopIntegration.label(new RunningWork.Item(WorkKind.GENERATION, "Aurora", null)))
                .isEqualTo("Génération d'une spécification du projet Aurora : elle est perdue, à relancer.");
    }

    final JiraWorld world = new JiraWorld();
    final TurnEvents turns = new TurnEvents(world.clock);
    final List<String> opened = new ArrayList<>();
    final List<String> asked = new ArrayList<>();
    boolean answer;
    final FakeDesktop desktop = new FakeDesktop();

    MacDesktopIntegration installed() {
        SynchronizeKnowledge sync = new SynchronizeKnowledge(world.projects, new InMemoryKnowledgeSources(),
                world.docSources, world.docs, world.folder, world.fileStates, (w, o) -> null, world.jira, world.tracker,
                world.ticketLinks, new InMemoryKnowledgeIndex(), new FakeEmbedding(), world.clock);
        MacDesktopIntegration mac = new MacDesktopIntegration();
        mac.work = new RunningWork(new Indexations(sync, world.projects, r -> { }, world.clock, false), turns,
                new LongWorkTracker(), world.projects, new InMemoryConversations());
        mac.opener = new TabOpener(new LaunchTokenService(java.util.Optional.empty()),
                new ManageBrowser(new InMemorySettings(), new BrowserPort() {
                    @Override
                    public List<Browser> installed() {
                        return List.of();
                    }

                    @Override
                    public boolean open(String browserId, String url) {
                        opened.add(url);
                        return true;
                    }
                }), 47615, true);
        mac.install(desktop, (title, text) -> {
            asked.add(text);
            return answer;
        });
        return mac;
    }

    @Test
    void unClicSurLeDockOuvreUnOngletAvecUnNouveauJeton() {
        installed();

        desktop.reopen.run();

        assertThat(opened).hasSize(1);
        assertThat(opened.getFirst()).startsWith("http://127.0.0.1:47615/?launch=");
    }

    @Test
    void quitterSansTravailSansQuestion() {
        installed();

        assertThat(desktop.quit()).isEqualTo("perform");
        assertThat(asked).isEmpty();
    }

    @Test
    void quitterAvecUnTravailDemandeConfirmation() {
        installed();
        turns.open(UUID.randomUUID(), TurnEvents.Kind.AGENT_REPLY, null);

        answer = false;
        assertThat(desktop.quit()).isEqualTo("cancel");
        answer = true;
        assertThat(desktop.quit()).isEqualTo("perform");
        assertThat(asked).hasSize(2).allMatch(t -> t.contains("Réponse de l'agent"));
    }

    /** Desktop simulé : garde les gestionnaires pour les déclencher. */
    static final class FakeDesktop implements MacDesktopIntegration.DesktopEvents {
        Consumer<MacDesktopIntegration.QuitResponse> quit;
        Runnable reopen;

        @Override
        public void onQuit(Consumer<MacDesktopIntegration.QuitResponse> handler) {
            quit = handler;
        }

        @Override
        public void onReopen(Runnable handler) {
            reopen = handler;
        }

        String quit() {
            List<String> seen = new ArrayList<>();
            quit.accept(new MacDesktopIntegration.QuitResponse() {
                @Override
                public void perform() {
                    seen.add("perform");
                }

                @Override
                public void cancel() {
                    seen.add("cancel");
                }
            });
            return String.join(",", seen);
        }
    }
}
