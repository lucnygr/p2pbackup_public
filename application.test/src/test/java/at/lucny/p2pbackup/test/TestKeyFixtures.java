package at.lucny.p2pbackup.test;

import at.lucny.p2pbackup.core.support.CryptoUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates the root keystore ({@code <user>.pfx}) and certificate ({@code <user>.pem}) for test users on demand.
 * Keys are generated once per JVM under {@code target/test-keys} and copied into the per-test config directories,
 * so no key material has to be committed and RSA key generation does not slow down every test.
 */
public final class TestKeyFixtures {

    private static final Map<String, Path> GENERATED = new ConcurrentHashMap<>();

    private static final Path KEY_DIR = Paths.get("target", "test-keys").toAbsolutePath();

    private TestKeyFixtures() {
    }

    public static String passwordFor(String user) {
        return "password" + user;
    }

    /** Copies keystore and certificate of the user into the given directory (generating them first if needed). */
    public static void copyTo(String user, Path targetDir) {
        try {
            Path source = GENERATED.computeIfAbsent(user, TestKeyFixtures::generate);
            Files.createDirectories(targetDir);
            for (String extension : new String[]{".pfx", ".pem"}) {
                Files.copy(source.resolve(user + extension), targetDir.resolve(user + extension), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path generate(String user) {
        try {
            Security.addProvider(new BouncyCastleProvider());
            Path dir = KEY_DIR.resolve(user);
            Files.deleteIfExists(dir.resolve(user + ".pfx"));
            Files.deleteIfExists(dir.resolve(user + ".pem"));
            Files.createDirectories(dir);
            new CryptoUtils().generateAuthenticationKeys(user, passwordFor(user).toCharArray(), dir.resolve(user + ".pfx"), dir.resolve(user + ".pem"));
            return dir;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

}
