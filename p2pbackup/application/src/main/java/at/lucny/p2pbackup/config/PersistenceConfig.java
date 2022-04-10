package at.lucny.p2pbackup.config;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.core.service.CryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.Console;
import java.io.IOException;
import java.util.HexFormat;
import java.util.Scanner;

@Configuration
public class PersistenceConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceConfig.class);

    private static final String SALT_KDF_DATABASE_KEY = "databaseKey";

    private static final String SALT_KDF_DATABASE_IV = "databaseIV";

    // http://hsqldb.org/doc/guide/management-chapt.html Kapitel "Encrypted Databases"
    @Bean
    public DataSource dataSource(@Value("${spring.datasource.url}") String databaseUrl, P2PBackupProperties p2PBackupProperties, CryptoService cryptoService) throws IOException {
        MACCheckingDataSource macCheckingDataSource = new MACCheckingDataSource(p2PBackupProperties, cryptoService);

        String dbUrl = databaseUrl;
        if (p2PBackupProperties.getDatabase().getEncrypt()) {
            String hexPassword = HexFormat.of().formatHex(cryptoService.getSecretKeyGenerator().generate(SALT_KDF_DATABASE_KEY).getEncoded());
            String hexIv = HexFormat.of().formatHex(cryptoService.getSecretKeyGenerator().generate(SALT_KDF_DATABASE_IV, 128).getEncoded());
            dbUrl = databaseUrl + ";crypt_key=" + hexPassword + ";crypt_iv=" + hexIv + ";crypt_type=AES/CBC/PKCS5Padding";
        } else {
            String yesNo = null;
            Console console = System.console();
            if (console != null) {
                yesNo = console.readLine("The database will not be stored encrypted, this is a security risk. Continue (Y/N)?:");
            } else {
                System.out.println("The database will not be stored encrypted, this is a security risk. Continue (Y/N)?:");
                Scanner scanner = new Scanner(System.in);
                yesNo = scanner.nextLine();
            }
            if (!"Y".equalsIgnoreCase(yesNo)) {
                throw new IllegalArgumentException("Abort startup because database would be unencrypted");
            }
        }
        LOGGER.debug("database url: {}", databaseUrl);
        var dataSourceBuilder = DataSourceBuilder.create();
        DataSource dataSource = dataSourceBuilder
                .url(dbUrl)
                .build();
        macCheckingDataSource.setTargetDataSource(dataSource);
        return macCheckingDataSource;
    }

}
