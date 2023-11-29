package at.lucny.p2pbackup.test.p2p.nextcloud;

import at.lucny.p2pbackup.test.p2p.BackupTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.DockerClientFactory;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Map;

@ExtendWith(SpringExtension.class)
@EnabledIf("isDockerAvailable")
public class NextcloudBackupTest extends BackupTest {

    private NextcloudBasedTestUtils nextcloudBasedTest;

    @BeforeEach
    protected void initNextcloud() throws NoSuchAlgorithmException, IOException, NoSuchProviderException, InterruptedException {
        this.nextcloudBasedTest = new NextcloudBasedTestUtils();
        this.nextcloudBasedTest.beforeEach("user1", "user2", "user3");
    }

    @AfterEach
    protected void shutdownNextcloud() {
        this.nextcloudBasedTest.afterEach();
    }

    @Override
    protected Map<String, Object> getProviderInformation(String user) {
        return this.nextcloudBasedTest.getProviderInformationForNextcloud(user);
    }

    protected static boolean isDockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

}
