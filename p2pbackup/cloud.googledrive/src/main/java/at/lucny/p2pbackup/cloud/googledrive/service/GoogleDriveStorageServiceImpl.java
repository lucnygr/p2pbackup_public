package at.lucny.p2pbackup.cloud.googledrive.service;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.cloud.CloudStorageService;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Validated
public class GoogleDriveStorageServiceImpl implements CloudStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleDriveStorageServiceImpl.class);

    private static final String CONFIG_KEY_ENABLED = "at.lucny.p2pbackup.cloud.googledrive.service.ENABLED";

    private final P2PBackupProperties p2PBackupProperties;

    private final Configuration configuration;

    private final Resource credentials;

    private Drive driveService;

    private File folderBackuploesung;

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Lists.newArrayList(DriveScopes.DRIVE_FILE);

    public GoogleDriveStorageServiceImpl(P2PBackupProperties p2PBackupProperties, Configuration configuration, @Value("classpath:credentials.json") Resource credentials) {
        this.p2PBackupProperties = p2PBackupProperties;
        this.configuration = configuration;
        this.credentials = credentials;

        if (!this.configuration.getBoolean(CONFIG_KEY_ENABLED, false)) {
            LOGGER.info("google-drive not enabled");
            return;
        }

        this.initialize();
    }

    @Override
    public void configure(Map<String, String> config) {
        if (config.containsKey("enabled") && Boolean.TRUE.equals(Boolean.valueOf(config.get("enabled")))) {
            this.configuration.setProperty(CONFIG_KEY_ENABLED, true);
            this.initialize();
        }
    }

    @Override
    public boolean isInitialized() {
        return this.driveService != null && this.folderBackuploesung != null;
    }

    private void checkInitialized() {
        if (!this.isInitialized()) {
            throw new IllegalStateException("google-drive wasn't initialized");
        }
    }

    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        if (credentials == null) {
            throw new IllegalStateException("google drive not configured");
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), new InputStreamReader(this.credentials.getInputStream()));

        Path credentialStore = this.p2PBackupProperties.getConfigDir().resolve("GoogleDriveCredentials");
        Files.createDirectories(credentialStore);
        FileDataStoreFactory fileDataStoreFactory = new FileDataStoreFactory(credentialStore.toFile());

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, GsonFactory.getDefaultInstance(), clientSecrets, SCOPES)
                .setDataStoreFactory(fileDataStoreFactory)
                .setAccessType("offline")
                //.setCredentialDataStore(fileDataStoreFactory.getDataStore("1"))
                .build();
        //LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize(this.p2PBackupProperties.getUser());
    }

    @Override
    public String getId() {
        return GoogleDriveStorageServiceImpl.class.getName();
    }

    private void initialize() {
        if (!this.configuration.getBoolean(CONFIG_KEY_ENABLED, false)) {
            LOGGER.info("google-drive not enabled");
            return;
        }

        // Build a new authorized API client service.
        try {
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            this.driveService = new Drive.Builder(httpTransport, GsonFactory.getDefaultInstance(), getCredentials(httpTransport))
                    .setApplicationName("at.lucny.p2pbackup.cloud.googledrive.service")
                    .build();

            FileList findFolder = this.driveService.files().list()
                    .setPageSize(5)
                    .setFields("files(id, parents, name)")
                    .setQ("mimeType = 'application/vnd.google-apps.folder' and name = 'Backuploesung'")
                    .execute();

            if (!findFolder.getFiles().isEmpty()) {
                this.folderBackuploesung = findFolder.getFiles().get(0);
            } else {
                LOGGER.info("creating folder 'Backuploesung' on google-drive");
                // create folder
                File fileMetadata = new File();
                fileMetadata.setName("Backuploesung");
                fileMetadata.setMimeType("application/vnd.google-apps.folder");

                this.folderBackuploesung = this.driveService.files().create(fileMetadata)
                        .setFields("id")
                        .execute();
            }
            LOGGER.debug("folder 'Backuploesung' has id {}", this.folderBackuploesung.getId());
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("unable to initialize google-drive", e);
        }
        LOGGER.info("initialized google-drive");
    }

    @Override
    @SneakyThrows
    public void upload(Path path) {
        LOGGER.trace("begin upload(path={})", path);
        this.checkInitialized();
        if (Files.notExists(path)) {
            throw new IllegalArgumentException("path " + path + " does not exist");
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("path " + path + " is not readable");
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("path " + path + " is not a regular file");
        }

        File fileMetadata = new File();
        fileMetadata.setName(path.getFileName().toString());
        fileMetadata.setParents(Collections.singletonList(this.folderBackuploesung.getId()));
        FileContent content = new FileContent("application/octet-stream", path.toFile());
        File f = this.driveService.files().create(fileMetadata, content).setFields("id,parents,name,properties,shared,webContentLink,webViewLink").execute();
        LOGGER.debug("file {} has file-id {}", path, f.getId());

        LOGGER.trace("end upload");
    }

    private Optional<File> findFile(String filename) throws IOException {
        String queryString = "name = '" + filename + "' and '" + this.folderBackuploesung.getId() + "' in parents";
        FileList result = this.driveService.files().list()
                .setQ(queryString)
                .setPageSize(2)
                .setFields("files(id, parents, name, shared, webContentLink)")
                .execute();
        List<File> files = result.getFiles();
        if (CollectionUtils.isEmpty(files)) {
            return Optional.empty();
        }
        if (files.size() > 1) {
            throw new IllegalStateException("found " + files.size() + " files for filename " + filename);
        }

        return Optional.of(files.get(0));
    }

    @Override
    @SneakyThrows
    public String share(String filename) {
        LOGGER.trace("begin share(filename={})", filename);
        Optional<File> fileOptional = this.findFile(filename);
        File file = fileOptional.orElseThrow(() -> new IllegalStateException("file " + filename + " not found"));

        Permission permission = new Permission();
        permission.setRole("reader");
        permission.setType("anyone");
        this.driveService.permissions().create(file.getId(), permission).execute();

        LOGGER.trace("end share: return={}", file.getWebContentLink());
        return file.getWebContentLink();
    }


    @Override
    @SneakyThrows
    public void delete(String filename) {
        LOGGER.trace("begin share(filename={})", filename);
        this.checkInitialized();
        Optional<File> fileOptional = this.findFile(filename);
        if (fileOptional.isPresent()) {
            LOGGER.debug("deleting {} with file-id {}", filename, fileOptional.get().getId());
            this.driveService.files().delete(fileOptional.get().getId()).execute();
        }
        LOGGER.trace("end delete");
    }
}
