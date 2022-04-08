package at.lucny.p2pbackup.test.p2p;

import at.lucny.p2pbackup.test.BaseTest;
import at.lucny.p2pbackup.user.service.UserService;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.ResourceUtils;
import org.springframework.util.SocketUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(SpringExtension.class)
abstract class BaseP2PTest extends BaseTest {

    @TempDir
    public Path tempDir;

    protected ConfigurableApplicationContext ctxUser1;

    protected ConfigurableApplicationContext ctxUser2;

    protected ConfigurableApplicationContext ctxUser3;

    private final Map<String, Integer> userToPort = new HashMap<>();

    protected void startContextForUser1() {
        this.ctxUser1 = this.createApplication("user1");

        this.addUser(this.ctxUser1, "user2", true, true);
        this.addUser(this.ctxUser1, "user3", true, true);
        if (this.ctxUser2 != null) {
            this.addUser(this.ctxUser2, "user1", true, true);
        }
        if (this.ctxUser3 != null) {
            this.addUser(this.ctxUser3, "user1", true, true);
        }
    }

    protected void startContextForUser2() {
        this.ctxUser2 = this.createApplication("user2");

        this.addUser(this.ctxUser2, "user1", true, true);
        this.addUser(this.ctxUser2, "user3", true, true);

        if (this.ctxUser1 != null) {
            this.addUser(this.ctxUser1, "user2", true, true);
        }
        if (this.ctxUser3 != null) {
            this.addUser(this.ctxUser3, "user2", true, true);
        }
    }

    protected void startContextForUser3() {
        this.ctxUser3 = this.createApplication("user3");

        this.addUser(this.ctxUser3, "user1", true, true);
        this.addUser(this.ctxUser3, "user2", true, true);

        if (this.ctxUser1 != null) {
            this.addUser(this.ctxUser1, "user3", true, true);
        }
        if (this.ctxUser2 != null) {
            this.addUser(this.ctxUser2, "user3", true, true);
        }
    }

    @SneakyThrows
    protected void addUser(ConfigurableApplicationContext ctx, String user, boolean allowBackupDataFromUser, boolean allowBackupDataToUser) {
        if (this.userToPort.containsKey(user)) {
            UserService userService = ctx.getBean(UserService.class);
            userService.addUser(user, "localhost", this.userToPort.get(user), ResourceUtils.getFile("classpath:" + user + "/" + user + ".cer").toPath(), allowBackupDataFromUser, allowBackupDataToUser, false);
        }
    }

    @SneakyThrows
    protected void copyFileToUserDataDir(String user, String filename) {
        Path dataDirUser1 = this.getDataDir(user);
        Path testfile1 = this.getFileFromClasspath(filename);
        Files.copy(testfile1, dataDirUser1.resolve(filename));
    }

    @SneakyThrows
    protected Path getFileFromClasspath(String filename) {
        File testfile1 = ResourceUtils.getFile("classpath:" + filename);
        return testfile1.toPath();
    }

    protected void stopContextForUser1() throws IOException {
        if (this.ctxUser1 != null) {
            this.ctxUser1.close();
            this.ctxUser1 = null;
        }
        this.cleanDirectories("user1");
    }

    protected void stopContextForUser2() throws IOException {
        if (this.ctxUser2 != null) {
            this.ctxUser2.close();
            this.ctxUser2 = null;
        }
        this.cleanDirectories("user2");
    }

    protected void stopContextForUser3() throws IOException {
        if (this.ctxUser3 != null) {
            this.ctxUser3.close();
            this.ctxUser3 = null;
        }
        this.cleanDirectories("user3");
    }

    private void cleanDirectories(String user) throws IOException {
        FileUtils.cleanDirectory(this.getConfigDir(user).toFile());
        FileUtils.cleanDirectory(this.getDataDir(user).toFile());
        FileUtils.cleanDirectory(this.getStorageDir(user).toFile());
        FileUtils.cleanDirectory(this.getCloudProviderDir(user).toFile());
        FileUtils.cleanDirectory(this.getRestoreDir(user).toFile());
    }


    @AfterEach
    void afterEach() throws IOException {
        this.stopContextForUser1();
        this.stopContextForUser2();
        this.stopContextForUser3();
    }

    protected Path getConfigDir(String user) {
        return createDirectory(tempDir.resolve(user).resolve("CONFIG"));
    }

    protected Path getDataDir(String user) {
        return createDirectory(tempDir.resolve(user).resolve("DATA"));
    }

    protected Path getStorageDir(String user) {
        return createDirectory(tempDir.resolve(user).resolve("STORAGE"));
    }

    protected Path getRestoreDir(String user) {
        return createDirectory(tempDir.resolve(user).resolve("RESTORE"));
    }

    protected Path getCloudProviderDir(String user) {
        return createDirectory(tempDir.resolve(user).resolve("CLOUD"));
    }

    protected ConfigurableApplicationContext createApplication(String user) {
        this.userToPort.put(user, SocketUtils.findAvailableTcpPort());

        SpringApplicationBuilder builder = new SpringApplicationBuilder(TestP2PBackupApplication.class);
        Map<String, Object> properties = new HashMap<>();
        properties.put("at.lucny.p2p-backup.user", user);
        properties.put("at.lucny.p2p-backup.network.port", this.userToPort.get(user));
        properties.put("at.lucny.p2p-backup.keystore", "classpath:" + user + "/" + user + ".pfx");
        properties.put("at.lucny.p2p-backup.password", "password" + user);
        properties.put("at.lucny.p2p-backup.config-dir", this.getConfigDir(user).toString());
        properties.put("at.lucny.p2p-backup.storage-dir", this.getStorageDir(user).toString());
        properties.put("at.lucny.p2p-backup.init.root-directories.0.name", "datadir_" + user);
        properties.put("at.lucny.p2p-backup.init.root-directories.0.path", this.getDataDir(user).toString());
        properties.put("at.lucny.p2p-backup.init.cloud-provider.0.id", "at.lucny.p2pbackup.cloud.filesystem.service.FilesystemStorageServiceImpl");
        properties.put("at.lucny.p2p-backup.init.cloud-provider.0.properties.directory", this.getCloudProviderDir(user).toString());
        properties.put("at.lucny.p2p-backup.init.disable-upload-agent", true);
        properties.put("at.lucny.p2p-backup.init.disable-distribution-agent", true);
        properties.put("at.lucny.p2p-backup.init.disable-restoration-agent", true);
        properties.put("spring.datasource.url", "jdbc:hsqldb:mem:" + user + System.currentTimeMillis() + ";shutdown=true");
        properties.put("spring.profiles.active", "integrationtest");
        List<String> arguments = properties.entrySet().stream().map(kv -> "--" + kv.getKey() + "=" + kv.getValue()).toList();
        return builder.run(arguments.toArray(new String[0]));
    }
}