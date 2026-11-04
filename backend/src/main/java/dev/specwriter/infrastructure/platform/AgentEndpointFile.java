package dev.specwriter.infrastructure.platform;

import dev.specwriter.domain.port.AgentEndpoint;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntSupplier;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fichier {@code agent-endpoint.json} du dossier de données : {@code {port, secret}}, lisible par
 * le seul utilisateur (research.md R2). Le secret fait 256 bits ; il survit aux redémarrages en
 * restant dans le fichier, et n'est écrit ni en base ni dans les journaux.
 */
public class AgentEndpointFile implements AgentEndpoint {

    public static final String FILE = "agent-endpoint.json";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path dir;
    private final Path file;
    private final IntSupplier port;
    private final boolean posix;
    private volatile String secret;

    public AgentEndpointFile(Path dataDir, IntSupplier port, boolean posix) {
        this.dir = dataDir;
        this.file = dataDir.resolve(FILE);
        this.port = port;
        this.posix = posix;
    }

    public Path file() {
        return file;
    }

    @Override
    public synchronized void publish() {
        if (secret == null) {
            secret = readSecret().orElseGet(AgentEndpointFile::newSecret);
        }
        write();
    }

    @Override
    public synchronized void withdraw() {
        secret = null;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Fichier des agents impossible à supprimer", e);
        }
    }

    @Override
    public synchronized void regenerate() {
        secret = newSecret();
        write();
    }

    /** Secret présenté par la passerelle, comparé à temps constant ; refusé si l'accès est coupé. */
    public boolean accepts(String presented) {
        String current = secret;
        if (current == null || presented == null) {
            return false;
        }
        return MessageDigest.isEqual(current.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }

    private void write() {
        try {
            Files.createDirectories(dir);
            // fichier temporaire créé en rw------- puis renommé : jamais lisible par un autre compte
            Path tmp = posix
                    ? Files.createTempFile(dir, "agent-endpoint", ".tmp",
                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                    : Files.createTempFile(dir, "agent-endpoint", ".tmp");
            Files.writeString(tmp, JSON.writeValueAsString(Map.of("port", port.getAsInt(), "secret", secret)));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Fichier des agents impossible à écrire", e);
        }
    }

    private Optional<String> readSecret() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            Object value = JSON.readValue(Files.readString(file), Map.class).get("secret");
            return value instanceof String s && s.length() >= 43 ? Optional.of(s) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
