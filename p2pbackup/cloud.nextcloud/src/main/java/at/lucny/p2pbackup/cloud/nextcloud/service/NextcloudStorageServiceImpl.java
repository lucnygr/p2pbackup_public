package at.lucny.p2pbackup.cloud.nextcloud.service;

import at.lucny.p2pbackup.application.support.StopApplicationEvent;
import at.lucny.p2pbackup.cloud.CloudStorageService;
import org.aarboard.nextcloud.api.NextcloudConnector;
import org.aarboard.nextcloud.api.exception.NextcloudApiException;
import org.aarboard.nextcloud.api.filesharing.Share;
import org.aarboard.nextcloud.api.filesharing.SharePermissions;
import org.aarboard.nextcloud.api.filesharing.ShareType;
import org.apache.commons.configuration2.Configuration;
import org.apache.http.client.HttpResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Validated
public class NextcloudStorageServiceImpl implements CloudStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NextcloudStorageServiceImpl.class);

    private static final String CONFIG_KEY_SERVICE_URL = "at.lucny.p2pbackup.cloud.nextcloud.service.SERVICE_URL";

    private static final String CONFIG_KEY_USERNAME = "at.lucny.p2pbackup.cloud.nextcloud.service.USERNAME";

    private static final String CONFIG_KEY_PASSWORD = "at.lucny.p2pbackup.cloud.nextcloud.service.PASSWORD";

    private static final String FOLDER_STORAGE = "at.lucny.p2pbackup.cloud.nextcloud";

    public static final String PROVIDER_ID = "at.lucny.p2pbackup.cloud.nextcloud.service.NextcloudStorageServiceImpl";

    private final Configuration configuration;

    private NextcloudConnector connector;

    public NextcloudStorageServiceImpl(Configuration configuration) {
        this.configuration = configuration;

        boolean hasServiceUrl = this.configuration.containsKey(CONFIG_KEY_SERVICE_URL);
        boolean hasUserName = this.configuration.containsKey(CONFIG_KEY_USERNAME);
        boolean hasPassword = this.configuration.containsKey(CONFIG_KEY_PASSWORD);

        if (!hasServiceUrl || !hasUserName || !hasPassword) {
            LOGGER.info("nextcloud not configured, skip initalization during startup");
            return;
        }
        this.initialize();
    }

    @EventListener
    public void onApplicationEvent(StopApplicationEvent event) throws IOException {
        this.stop();
    }

    @PreDestroy
    public void stop() throws IOException {
        LOGGER.trace("begin stop()");
        if (this.connector != null) {
            LOGGER.info("shutting down nextcloud-connection");
            this.connector.shutdown();
        }
        LOGGER.trace("end stop()");
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    private void initialize() {
        String serviceUrl = Objects.requireNonNull(this.configuration.getString(CONFIG_KEY_SERVICE_URL), "nextcloud not configured, property " + CONFIG_KEY_SERVICE_URL + " missing");
        String username = Objects.requireNonNull(this.configuration.getString(CONFIG_KEY_USERNAME), "nextcloud not configured, property " + CONFIG_KEY_USERNAME + " missing");
        String password = Objects.requireNonNull(this.configuration.getString(CONFIG_KEY_PASSWORD), "nextcloud not configured, property " + CONFIG_KEY_PASSWORD + " missing");

        try {
            LOGGER.trace("try stopping nextcloud if already configured and running");
            this.stop();
        } catch (IOException e) {
            throw new IllegalStateException("could not stop connector", e);
        }
        this.connector = new NextcloudConnector(serviceUrl, username, password);
        if (!this.connector.folderExists(FOLDER_STORAGE)) {
            LOGGER.info("create folder {} on nextcloud", FOLDER_STORAGE);
            this.connector.createFolder(FOLDER_STORAGE);
        }
        LOGGER.info("initialized nextcloud for serviceUrl {}", serviceUrl);
    }

    public void configure(String serviceUrl, String username, String password) {
        this.configuration.setProperty(CONFIG_KEY_SERVICE_URL, serviceUrl);
        this.configuration.setProperty(CONFIG_KEY_USERNAME, username);
        this.configuration.setProperty(CONFIG_KEY_PASSWORD, password);
        LOGGER.debug("configured nextcloud for serviceUrl {}", serviceUrl);

        this.initialize();
    }

    @Override
    public void configure(Map<String, String> config) {
        String serviceUrl = Objects.requireNonNull(config.get("service-url"), "Missing property service-url");
        String username = Objects.requireNonNull(config.get("username"), "Missing property username");
        String password = Objects.requireNonNull(config.get("password"), "Missing property password");
        this.configure(serviceUrl, username, password);
    }

    @Override
    public boolean isInitialized() {
        return this.connector != null;
    }

    private void checkInitialized() {
        if (!this.isInitialized()) {
            throw new IllegalStateException("nextcloud wasn't initialized");
        }
    }

    private String getRemotePath(String filename) {
        return FOLDER_STORAGE + "/" + filename;
    }

    @Override
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

        String remoteFilename = this.getRemotePath(path.getFileName().toString());
        this.connector.uploadFile(path.toFile(), remoteFilename);
        LOGGER.trace("end upload(path={})", path);
    }

    @Override
    public List<String> list() {
        this.checkInitialized();
        List<String> files = new ArrayList<>(this.connector.listFolderContent(FOLDER_STORAGE));
        files.remove(FOLDER_STORAGE);
        return files;
    }

    @Override
    public String share(String filename) {
        LOGGER.trace("begin share(filename={})", filename);
        this.checkInitialized();
        String remoteFilename = this.getRemotePath(filename);
        try {
            String publicUrl = this.shareInternal(remoteFilename);
            LOGGER.trace("end share(filename={}): return={}", filename, publicUrl);
            return publicUrl;
        } catch (NextcloudApiException e) {
            if (e.getCause() instanceof HttpResponseException ex && ex.getStatusCode() == 404) {// ignore 404 because this means the file as already deleted
                throw new IllegalArgumentException("remote-path " + remoteFilename + " does not exist");
            }
            throw e;
        }
    }

    private String shareInternal(String remoteFilename) {
        Share share = connector.doShare(remoteFilename, ShareType.PUBLIC_LINK, null, false, null, new SharePermissions(SharePermissions.SingleRight.READ));
        LOGGER.debug("shared remote file {}", remoteFilename);
        return share.getUrl() + "/download";
    }

    public void delete(String filename) {
        LOGGER.trace("begin delete(filename={})", filename);
        this.checkInitialized();
        String remoteFilename = this.getRemotePath(filename);
        try {
            this.connector.removeFile(remoteFilename);
        } catch (NextcloudApiException e) {
            if (e.getCause() instanceof HttpResponseException ex && ex.getStatusCode() == 404) {// ignore 404 because this means the file as already deleted
                return;
            }
            throw e;
        }
        LOGGER.trace("end delete(filename={})", filename);
    }

    @Override
    public String toString() {
        return NextcloudStorageServiceImpl.class + "-" + this.getId();
    }
}
