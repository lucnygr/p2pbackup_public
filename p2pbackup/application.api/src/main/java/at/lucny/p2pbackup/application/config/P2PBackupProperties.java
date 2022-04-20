package at.lucny.p2pbackup.application.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.unit.DataSize;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.nio.file.Path;

@Configuration
@ConfigurationProperties(prefix = "at.lucny.p2p-backup")
@Data
@ToString
public class P2PBackupProperties {

    private InitializationProperties init;

    @NotNull
    private String user;

    @NotNull
    private Path configDir;

    @NotNull
    private Resource keystore;

    @NotNull
    private Resource certificate;

    private Resource oldKeystore;

    @NotNull
    private Path storageDir;

    @NotNull
    private NetworkProperties network = new NetworkProperties();

    @NotNull
    private DatabaseProperties database = new DatabaseProperties();

    @NotNull
    private VerificationProperties verificationProperties = new VerificationProperties();

    @NotNull
    @Min(2)
    private Integer minimalReplicas = 3;

    @NotNull
    private DataSize blockSize = DataSize.ofKilobytes(500);
}
