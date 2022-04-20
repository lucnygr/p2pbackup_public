package at.lucny.p2pbackup.config;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.core.service.CryptoService;
import at.lucny.p2pbackup.core.support.HashUtils;
import at.lucny.p2pbackup.core.support.UserInputHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.crypto.SecretKey;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.List;
import java.util.stream.StreamSupport;

public class MACCheckingDataSource extends DelegatingDataSource implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MACCheckingDataSource.class);

    private static final String SALT_KDF_DATABASE_MAC = "databaseMAC";

    private static final String FILE_DB_MAC = "databaseMAC";

    private final P2PBackupProperties p2PBackupProperties;

    private final SecretKey macKey;

    public MACCheckingDataSource(P2PBackupProperties p2PBackupProperties, CryptoService cryptoService) throws IOException {
        this.p2PBackupProperties = p2PBackupProperties;
        this.macKey = cryptoService.getSecretKeyGenerator().generate(SALT_KDF_DATABASE_MAC, 128);

        Path databaseMacPath = this.p2PBackupProperties.getConfigDir().resolve(FILE_DB_MAC);
        LOGGER.info("checking MAC of database {}", databaseMacPath);

        boolean dbMacExists = Files.isRegularFile(databaseMacPath);
        boolean databaseExists = Files.isDirectory(this.p2PBackupProperties.getDatabase().getDatabaseDir());

        if (dbMacExists && databaseExists) {
            String actualMacForDb = this.generateMacForDb();
            String expectedMacForDb = Files.readString(databaseMacPath);

            if (!expectedMacForDb.equals(actualMacForDb)) {
                String yesNo = new UserInputHelper().read("MAC of database is not correct. Continue anyway? (Y/N):");
                if (!"Y".equalsIgnoreCase(yesNo)) {
                    throw new IllegalStateException("MAC of database is incorrect");
                }
            }
        } else if (!dbMacExists && !databaseExists) {
            LOGGER.debug("fresh start without any database");
        } else {
            throw new IllegalStateException("MAC of database does not exist");
        }
    }

    @Override
    public void close() throws IOException {
        if (this.getTargetDataSource() != null && this.getTargetDataSource() instanceof Closeable datasource) {
            datasource.close();
        }

        String macForDb = this.generateMacForDb();

        Path databaseMacPath = this.p2PBackupProperties.getConfigDir().resolve(FILE_DB_MAC);
        Files.writeString(databaseMacPath, macForDb, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

        LOGGER.debug("closing datdasource finished");
    }

    private String generateMacForDb() {
        try {
            try (DirectoryStream<Path> fileIndir = Files.newDirectoryStream(this.p2PBackupProperties.getDatabase().getDatabaseDir(), "hsqldb*")) {
                List<byte[]> dbFiles = StreamSupport.stream(fileIndir.spliterator(), false).sorted()
                        .filter((Files::isRegularFile))
                        .map(path -> {
                            try {
                                byte[] bytes = Files.readAllBytes(path);
                                LOGGER.trace("found db-file {} with bytes {}", path, Base64.getEncoder().encodeToString(bytes));
                                return bytes;
                            } catch (IOException e) {
                                throw new IllegalStateException("could not read db file " + path);
                            }
                        }).toList();

                HashUtils hashUtils = new HashUtils();
                return hashUtils.generateMac(this.macKey, dbFiles);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to generate MAC for db " + this.p2PBackupProperties.getDatabase().getDatabaseDir());
        }
    }
}
