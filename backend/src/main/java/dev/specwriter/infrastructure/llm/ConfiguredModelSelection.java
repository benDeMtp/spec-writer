package dev.specwriter.infrastructure.llm;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Choix du modèle par configuration ; remplacé par le choix de l'utilisateur en US1 (FR-030c). */
@ApplicationScoped
@DefaultBean
public class ConfiguredModelSelection implements ModelSelection {

    @ConfigProperty(name = "specwriter.copilot.model")
    Optional<String> model;

    @Override
    public Optional<String> currentModel() {
        return model.filter(m -> !m.isBlank());
    }
}
