package at.lucny.p2pbackup;

import at.lucny.p2pbackup.core.support.CryptoUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;

/**
 * Usage: call application with the following parameters [username] [password] [storagepath]
 * This will generate a keystore [username].pfx and the according certificate [username].pem under the given [storagepath].
 */
public class P2PBackupKeyGenerator {

    public static void main(String[] args) throws NoSuchAlgorithmException, NoSuchProviderException, IOException {
        Security.addProvider(new BouncyCastleProvider()); // register bouncycastle-provider

        String username = args[0];
        String password = args[1];
        String storagePath = args[2];
        new CryptoUtils().generateAuthenticationKeys(username, password.toCharArray(), Paths.get(storagePath).resolve(username + ".pfx"), Paths.get(storagePath).resolve(username + ".pem"));
    }
}
