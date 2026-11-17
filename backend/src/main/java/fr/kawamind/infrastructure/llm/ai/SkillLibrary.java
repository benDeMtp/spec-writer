package fr.kawamind.infrastructure.llm.ai;

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consignes réutilisables (014, FR-011, research R3) : une consigne d'opération déclare, seule sur sa
 * ligne, {@code [skill:<nom>]} ; la ressource Markdown {@code prompts/skills/<nom>.md} prend sa place
 * dans le message système, jamais dans le message utilisateur. Une consigne réutilisable absente
 * arrête le démarrage. Le {@code SkillProvider} de la CLI Copilot n'est pas retenu.
 */
@ApplicationScoped
public class SkillLibrary {

    static final String FOLDER = "prompts/skills/";
    static final Pattern DIRECTIVE = Pattern.compile("^\\[skill:([a-z0-9-]+)]\\s*$", Pattern.MULTILINE);
    private static final Map<String, String> LOADED = new ConcurrentHashMap<>();

    /** Au démarrage : chaque consigne réutilisable déclarée par un AI Service existe. */
    void onStart(@Observes StartupEvent event) {
        Set<Class<?>> services = new LinkedHashSet<>();
        Arc.container().beanManager().getBeans(Object.class, Any.Literal.INSTANCE).forEach(bean -> {
            for (Type type : bean.getTypes()) {
                if (type instanceof Class<?> c && c.isInterface() && c.isAnnotationPresent(RegisterAiService.class)) {
                    services.add(c);
                }
            }
        });
        check(services);
    }

    /** Charge les consignes réutilisables déclarées par les consignes de ces AI Services. */
    static void check(Collection<Class<?>> services) {
        for (Class<?> service : services) {
            for (Method method : service.getMethods()) {
                SystemMessage system = method.getAnnotation(SystemMessage.class);
                if (system != null && !system.fromResource().isBlank()) {
                    declared(read(system.fromResource())
                            .orElseThrow(() -> new IllegalStateException("Consigne introuvable : "
                                    + system.fromResource()))).forEach(SkillLibrary::load);
                }
            }
        }
    }

    /** Message système dont chaque déclaration est remplacée par sa consigne réutilisable. */
    public static String expand(String system) {
        if (system == null || !system.contains("[skill:")) {
            return system;
        }
        Matcher m = DIRECTIVE.matcher(system);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(load(m.group(1)).strip()));
        }
        return m.appendTail(out).toString();
    }

    static List<String> declared(String system) {
        List<String> names = new ArrayList<>();
        Matcher m = DIRECTIVE.matcher(system);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    static String load(String name) {
        return LOADED.computeIfAbsent(name, n -> read(FOLDER + n + ".md")
                .orElseThrow(() -> new IllegalStateException("Consigne réutilisable introuvable : " + FOLDER + n
                        + ".md")));
    }

    private static java.util.Optional<String> read(String resource) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(resource)) {
            return in == null ? java.util.Optional.empty()
                    : java.util.Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
