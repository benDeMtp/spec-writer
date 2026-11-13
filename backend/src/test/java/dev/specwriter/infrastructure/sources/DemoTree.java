package dev.specwriter.infrastructure.sources;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Copie de l'arbre de démonstration {@code e2e/fixtures/sources/demo-rv} avec les pièges que le
 * dépôt ne peut pas versionner (voir e2e/fixtures/sources/demo-rv.md) : fichiers sensibles, dossiers générés, binaire et lien
 * symbolique vers l'extérieur. Tous contiennent {@code ContractStatus}.
 */
public final class DemoTree {

    public static final Path SOURCE = Path.of("../e2e/fixtures/sources/demo-rv").toAbsolutePath().normalize();

    private DemoTree() {
    }

    /** Copie l'arbre dans {@code target}/demo-rv, ajoute les pièges, et renvoie la racine. */
    public static Path copy(Path target) {
        Path root = target.resolve("demo-rv");
        try {
            copyTree(SOURCE, root);
            String trap = "ContractStatus piège\n";
            Files.writeString(root.resolve(".env"), "SECRET=" + trap);
            Files.writeString(root.resolve("modules/facturation/settings.xml"), "<settings>" + trap + "</settings>");
            write(root.resolve("front/node_modules/x/index.js"), trap);
            write(root.resolve("modules/facturation/target/gen/A.java"), trap);
            byte[] binary = ("\0" + trap).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Files.write(root.resolve("libs/common-contract/data.bin"), binary);
            Path outside = Files.createDirectories(target.resolve("hors-arbre"));
            Files.writeString(outside.resolve("Externe.java"), trap);
            try {
                Files.createSymbolicLink(root.resolve("modules/lien-externe"), outside);
            } catch (UnsupportedOperationException | IOException e) {
                // système sans liens symboliques : le piège est simplement absent
            }
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
