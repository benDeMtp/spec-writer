package dev.specwriter.infrastructure.platform;

import io.quarkus.runtime.LaunchMode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;

/** Choix du port d'écoute local : le port configuré s'il est libre, sinon un port libre (R14). */
public final class PortSelector {

    public static final int DEFAULT_PORT = 47615;

    private PortSelector() {
    }

    /**
     * En dev mode, le port configuré est gardé tel quel : le serveur HTTP survit au rechargement à
     * chaud, et c'est lui qui occupe le port (research.md R17).
     */
    public static int choose(int preferred, LaunchMode mode) {
        return mode == LaunchMode.DEVELOPMENT && preferred > 0 ? preferred : choose(preferred);
    }

    public static int choose(int preferred) {
        if (preferred > 0 && isFree(preferred)) {
            return preferred;
        }
        // Sonde de port libre sur la boucle locale, fermée aussitôt : aucune donnée n'y transite.
        // nosemgrep: java.lang.security.audit.crypto.unencrypted-socket.unencrypted-socket
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException("Aucun port local libre", e);
        }
    }

    private static boolean isFree(int port) {
        // nosemgrep: java.lang.security.audit.crypto.unencrypted-socket.unencrypted-socket
        try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
