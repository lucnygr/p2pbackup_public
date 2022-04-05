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
import java.io.IOException;
import java.util.HexFormat;

@Configuration
public class PersistenceConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceConfig.class);

    private static final String SALT_KDF_DATABASE_KEY = "databaseKey";

    private static final String SALT_KDF_DATABASE_IV = "databaseIV";

    // http://hsqldb.org/doc/guide/management-chapt.html Kapitel "Encrypted Databases"
    @Bean
    public DataSource dataSource(@Value("${spring.datasource.url}") String databaseUrl, P2PBackupProperties p2PBackupProperties, CryptoService cryptoService) throws IOException {
        MACCheckingDataSource macCheckingDataSource = new MACCheckingDataSource(p2PBackupProperties, cryptoService);

        String hexPassword = HexFormat.of().formatHex(cryptoService.getSecretKeyGenerator().generate(SALT_KDF_DATABASE_KEY).getEncoded());
        String hexIv = HexFormat.of().formatHex(cryptoService.getSecretKeyGenerator().generate(SALT_KDF_DATABASE_IV, 128).getEncoded());
        var dataSourceBuilder = DataSourceBuilder.create();
        String dbUrl = databaseUrl + ";crypt_key=" + hexPassword + ";crypt_iv=" + hexIv + ";crypt_type=AES/CBC/PKCS5Padding";
        LOGGER.trace("database url: {}", dbUrl);
        DataSource dataSource = dataSourceBuilder
                .url(dbUrl)
                .build();
        macCheckingDataSource.setTargetDataSource(dataSource);
        return macCheckingDataSource;
    }

}
