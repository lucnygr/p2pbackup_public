package at.lucny.p2pbackup.test;

import at.lucny.p2pbackup.core.support.CryptoUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Security;

/**
 * Generates the root keystore ({@code <user>.pfx}) and certificate ({@code <user>.pem}) for test users,
 * so no key material has to be committed.
 */
public final class TestKeyFixtures {

    private TestKeyFixtures() {
    }

    public static String passwordFor(String user) {
        return "password" + user;
    }

    /** Generates new keys for the user in the given directory. */
    public static void generateTo(String user, Path targetDir) {
        try {
            Security.addProvider(new BouncyCastleProvider());
            Files.createDirectories(targetDir);
            new CryptoUtils().generateAuthenticationKeys(user, passwordFor(user).toCharArray(), targetDir.resolve(user + ".pfx"), targetDir.resolve(user + ".pem"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
