package at.lucny.p2pbackup.test.p2p.nextcloud;

import lombok.SneakyThrows;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class NextcloudBasedTestUtils {

    private final GenericContainer nextcloudContainer =
            new GenericContainer(DockerImageName.parse("nextcloud:27"))
                    .withExposedPorts(80)
                    .waitingFor(Wait.forHttp("/"))
                    .withEnv("SQLITE_DATABASE", "nextcloud")
                    .withEnv("NEXTCLOUD_ADMIN_USER", "admin")
                    .withEnv("NEXTCLOUD_ADMIN_PASSWORD", "password")
                    .withEnv("OC_PASS", "095jklfds109");

    public void beforeEach(String... users) throws IOException, InterruptedException {
        this.nextcloudContainer.start();

        for (String user : users) {
            org.testcontainers.containers.Container.ExecResult result = this.nextcloudContainer.execInContainerWithUser("www-data", "php", "occ", "user:add", "--password-from-env", "--group=admin", user);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("Unable to create users in nextcloud. StdOut: " + result.getStdout() + ", StdErr: " + result.getStderr());
            }

        }
    }

    @SneakyThrows
    public void afterEach() {
        this.nextcloudContainer.stop();
    }

    public Map<String, Object> getProviderInformationForNextcloud(String user) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("at.lucny.p2p-backup.init.cloud-provider.0.id", "at.lucny.p2pbackup.cloud.nextcloud.service.NextcloudStorageServiceImpl");
        properties.put("at.lucny.p2p-backup.init.cloud-provider.0.properties.service-url", "http://" + this.nextcloudContainer.getHost() + ":" + this.nextcloudContainer.getMappedPort(80));
        properties.put("at.lucny.p2p-backup.init.cloud-provider.0.properties.username", user);
        properties.put("at.lucny.p2p-backup.init.cloud-provider.0.properties.password", "095jklfds109");
        return properties;
    }
}
