package at.lucny.p2pbackup.test.p2p;

import at.lucny.p2pbackup.P2PBackupApplicationConfiguration;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import java.security.Security;

@SpringBootApplication
@Import(P2PBackupApplicationConfiguration.class)
public class TestP2PBackupApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestP2PBackupApplication.class, args);
    }

}
