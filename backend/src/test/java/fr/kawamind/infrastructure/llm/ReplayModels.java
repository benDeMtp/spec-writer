package fr.kawamind.infrastructure.llm;

import dev.langchain4j.model.chat.ChatModel;
import java.nio.file.Path;

/** Accès de test au modèle rejoué d'un dossier de fixtures. */
public final class ReplayModels {

    private ReplayModels() {
    }

    public static ChatModel chat(Path dir) {
        return new ReplayChatModel(dir);
    }
}
