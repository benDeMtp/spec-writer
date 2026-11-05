package dev.specwriter.support;

import dev.specwriter.domain.port.EmbeddingPort;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Modèle d'embedding déterministe : un vecteur tiré du texte ; état et identifiant réglables. */
public class FakeEmbedding implements EmbeddingPort {

    public State state = State.MISSING;
    public State prepared = State.READY;
    public String modelId = "fake@1";
    /** Textes vectorisés comme passages, dans l'ordre. */
    public final List<String> passages = new ArrayList<>();

    @Override
    public State state() {
        return state;
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public State prepare(Progress progress) {
        state = prepared;
        return state;
    }

    @Override
    public float[] query(String text) {
        return state == State.READY ? vector(text) : null;
    }

    @Override
    public List<float[]> passages(List<String> texts) {
        if (state != State.READY) {
            return List.of();
        }
        passages.addAll(texts);
        return texts.stream().map(FakeEmbedding::vector).toList();
    }

    public static float[] vector(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        float[] v = new float[8];
        for (int i = 0; i < bytes.length; i++) {
            v[i % 8] += bytes[i];
        }
        double norm = 0;
        for (float f : v) {
            norm += f * f;
        }
        for (int i = 0; i < 8; i++) {
            v[i] /= (float) Math.max(Math.sqrt(norm), 1e-9);
        }
        return v;
    }
}
