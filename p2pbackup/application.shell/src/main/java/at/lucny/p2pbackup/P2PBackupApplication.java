package at.lucny.p2pbackup;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import java.security.Security;

@SpringBootApplication
@Import(P2PBackupApplicationConfiguration.class)
public class P2PBackupApplication {

    public static void main(String[] args) {
        Security.addProvider(new BouncyCastleProvider()); // register bouncycastle-provider

        SpringApplication.run(P2PBackupApplication.class, args);
    }
}
