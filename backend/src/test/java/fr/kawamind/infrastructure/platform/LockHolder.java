package fr.kawamind.infrastructure.platform;

import java.nio.file.Path;

/** Processus enfant de {@link InstanceLockTest} : tient le verrou du dossier donné jusqu'à sa mort. */
public final class LockHolder {

    private LockHolder() {
    }

    public static void main(String... args) throws Exception {
        System.out.println(InstanceLock.tryAcquire(Path.of(args[0])));
        System.out.flush();
        Thread.sleep(Long.MAX_VALUE);
    }
}
