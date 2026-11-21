package fr.kawamind.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.application.lifecycle.LongWorkTracker;
import fr.kawamind.application.lifecycle.WorkKind;
import fr.kawamind.domain.project.Project;
import fr.kawamind.support.InMemoryProjects;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T014 : une génération est comptée pendant son traitement, puis retirée (019, research.md R4). */
class LongWorkFilterTest {

    final LongWorkTracker tracker = new LongWorkTracker();
    final LongWorkFilter filter = new LongWorkFilter(tracker, new InMemoryProjects());
    final Map<String, Object> properties = new HashMap<>();
    final ContainerRequestContext request = (ContainerRequestContext) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] { ContainerRequestContext.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "setProperty" -> properties.put((String) args[0], args[1]);
                case "getProperty" -> properties.get((String) args[0]);
                case "removeProperty" -> properties.remove((String) args[0]);
                default -> throw new UnsupportedOperationException(method.getName());
            });

    @Test
    void compteePendantLeTraitementPuisRetiree() {
        filter.filter(request);

        assertThat(tracker.current()).containsExactly(new LongWorkTracker.Entry(WorkKind.GENERATION, Project.DEFAULT_ID));

        filter.filter(request, null);
        assertThat(tracker.current()).isEmpty();
    }

    @Test
    void uneReponseSansRequeteCompteeEstSansEffet() {
        filter.filter(request, null);
        filter.filter(request, null);

        assertThat(tracker.current()).isEmpty();
    }
}
