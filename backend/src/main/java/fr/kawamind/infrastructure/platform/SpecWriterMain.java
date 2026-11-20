package fr.kawamind.infrastructure.platform;

import fr.kawamind.domain.port.ClockPort;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Point d'entrée de l'outil (019, research.md R1, R3, R5). En mode normal (jar, installeurs, E2E),
 * une seule instance par dossier de données : un second lancement demande à l'instance en marche
 * d'ouvrir un onglet, puis se termine. Le dev mode passe aussi par ici, à chaque rechargement à
 * chaud : il n'est pas concerné.
 */
@QuarkusMain
public final class SpecWriterMain {

    private SpecWriterMain() {
    }

    public static void main(String... args) {
        if (LaunchMode.current() == LaunchMode.NORMAL) {
            SecondLaunch.Result result = claim();
            if (!result.start()) {
                System.exit(result.exitCode());
                return;
            }
        }
        Quarkus.run(args);
    }

    /** Prend le verrou, ou joue le second lancement. */
    static SecondLaunch.Result claim() {
        UserDirectories dirs = UserDirectories.fromSystem();
        Path data;
        try {
            data = UserDirectories.createPrivateDirectories(dirs.dataDir(), dirs.posix());
            if (InstanceLock.tryAcquire(data) == InstanceLock.Outcome.ACQUIRED) {
                return new SecondLaunch.Result(true, 0);
            }
        } catch (IOException | RuntimeException e) {
            // dossier inutilisable : pas une instance en marche ; le démarrage échouera en le nommant
            return new SecondLaunch.Result(true, 0);
        }
        ClockPort clock = Instant::now;
        return new SecondLaunch(() -> acquire(data), new ChannelClient(data.resolve(ChannelProtocol.SOCKET),
                Duration.ofSeconds(5)), clock, d -> Thread.sleep(d), LauncherMessages.system()).run();
    }

    private static boolean acquire(Path data) {
        try {
            return InstanceLock.tryAcquire(data) == InstanceLock.Outcome.ACQUIRED;
        } catch (IOException e) {
            return false;
        }
    }
}
