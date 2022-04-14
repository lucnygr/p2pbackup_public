package at.lucny.p2pbackup.localstorage.service;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.core.support.*;
import at.lucny.p2pbackup.localstorage.dto.LocalStorageEntry;
import at.lucny.p2pbackup.network.dto.BackupBlock;
import at.lucny.p2pbackup.network.dto.BackupBlockFailure;
import at.lucny.p2pbackup.user.domain.User;
import at.lucny.p2pbackup.user.repository.UserRepository;
import at.lucny.p2pbackup.user.support.UserAddedEvent;
import at.lucny.p2pbackup.user.support.UserChangedEvent;
import at.lucny.p2pbackup.user.support.UserDeletedEvent;
import lombok.SneakyThrows;
import org.apache.commons.codec.digest.HmacUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.CollectionUtils;
import org.springframework.util.FileSystemUtils;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@Validated
public class LocalStorageServiceImpl implements LocalStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalStorageServiceImpl.class);

    public static final String FOLDER_BACKUP = "_BACKUP";

    public static final String FOLDER_DELETED = "_DELETED";

    private final P2PBackupProperties p2PBackupProperties;

    private final UserRepository userRepository;

    private final CryptoUtils cryptoUtils;

    private final FileUtils fileUtils = new FileUtils();

    public LocalStorageServiceImpl(P2PBackupProperties p2PBackupProperties, UserRepository userRepository, CryptoUtils cryptoUtils) {
        this.p2PBackupProperties = p2PBackupProperties;
        this.userRepository = userRepository;
        this.cryptoUtils = cryptoUtils;
    }

    @Override
    @PostConstruct
    public void initializeDirectories() throws IOException {
        Files.createDirectories(p2PBackupProperties.getStorageDir());
        Files.createDirectories(p2PBackupProperties.getStorageDir().resolve(FOLDER_BACKUP));
        List<User> users = this.userRepository.findAll();
        for (User user : users) {
            this.initializeDirectories(user.getId());
        }
    }

    private void initializeDirectories(String userId) throws IOException {
        User user = this.userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("unknown user " + userId));
        if (user.isAllowBackupDataFromUser()) {
            LOGGER.info("initializing local-storage-directories for user {}", userId);
            Path userDirectory = this.p2PBackupProperties.getStorageDir().resolve(userId);
            Files.createDirectories(userDirectory);
            Path userDirectoryForDeletedBlocks = userDirectory.resolve(FOLDER_DELETED);
            Files.createDirectories(userDirectoryForDeletedBlocks);
        } else {
            LOGGER.debug("user {} is not allowed to store backup-data", userId);
        }
    }

    @TransactionalEventListener
    public void afterUserAdded(UserAddedEvent event) throws IOException {
        this.initializeDirectories(event.getUserId());
    }

    @TransactionalEventListener
    public void afterUserAdded(UserChangedEvent event) throws IOException {
        this.initializeDirectories(event.getUserId());
    }

    @TransactionalEventListener
    public void afterUserDeleted(UserDeletedEvent event) throws IOException {
        LOGGER.info("removing local-storage directories for user {}", event.getUserId());
        Path userDirectory = this.p2PBackupProperties.getStorageDir().resolve(event.getUserId());
        FileSystemUtils.deleteRecursively(userDirectory);
    }

    @Override
    public LocalStorageEntry saveInLocalStorage(String id, ByteBuffer block) {
        LOGGER.trace("begin saveInLocalBackup({}, {})", id, block);

        Path blockPath = this.p2PBackupProperties.getStorageDir().resolve(FOLDER_BACKUP).resolve(id);

        byte[] secret = new byte[16];
        this.cryptoUtils.getSecureRandom().nextBytes(secret);
        Mac mac = HmacUtils.getInitializedMac(CryptoConstants.HMAC_BLOCK_ALGORITHM, secret);

        LOGGER.debug("persisting local-backup-block for {}", id);
        try (MacOutputStream dos = new MacOutputStream(Files.newOutputStream(blockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING), mac);
             WritableByteChannel fileChannel = Channels.newChannel(dos)) {
            fileChannel.write(block.duplicate());
        } catch (IOException e) {
            throw new IllegalStateException("unable to write block to storage file " + blockPath, e);
        }
        byte[] calculatedMac = mac.doFinal();

        String macSecret = Base64.getEncoder().encodeToString(secret);
        String encryptedBlockMac = Base64.getEncoder().encodeToString(calculatedMac);
        LocalStorageEntry result = new LocalStorageEntry(id, macSecret, encryptedBlockMac);

        LOGGER.trace("end saveInLocalBackup: return {}", result);
        return result;
    }

    @Override
    public boolean removeFromLocalStorage(String id) {
        Path blockPath = this.p2PBackupProperties.getStorageDir().resolve(FOLDER_BACKUP).resolve(id);
        try {
            Files.deleteIfExists(blockPath);
            return true;
        } catch (IOException e) {
            LOGGER.warn("unable to remove block {} with path {} from local storage", id, blockPath);
        }
        return false;
    }

    @Override
    public void removeFromLocalStorage(String userId, List<String> ids) {
        Path deleteFolder = this.p2PBackupProperties.getStorageDir().resolve(userId).resolve(FOLDER_DELETED);

        for (String blockId : ids) {
            Path blockPath = this.p2PBackupProperties.getStorageDir().resolve(userId).resolve(blockId);
            if (Files.exists(blockPath)) {
                String filename = LocalDateTime.now(ZoneOffset.UTC).plusMonths(3).toLocalDate().toString() + "_" + blockId;
                Path destinationPath = deleteFolder.resolve(filename);
                try {
                    Files.move(blockPath, destinationPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    LOGGER.warn("unable to move file {} to {}", blockPath, destinationPath);
                }
            }
        }
    }

    private boolean isUserAllowedToBackupData(String userId) {
        User user = this.userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User " + userId + " unknown"));
        return user.isAllowBackupDataFromUser();
    }

    @Override
    public Optional<BackupBlockFailure.BackupBlockFailureType> saveFromUserInLocalStorage(String userId, BackupBlock backupBlock) {
        LOGGER.trace("begin saveFromUserInLocalBackup({}, {})", userId, backupBlock);

        if (!this.isUserAllowedToBackupData(userId)) {
            LOGGER.info("received backup block {}, but user {} is not allowed to save backups", backupBlock.getId(), userId);
            LOGGER.trace("end saveFromUserInLocalBackup: return {}", BackupBlockFailure.BackupBlockFailureType.USER_NOT_ALLOWED);
            return Optional.of(BackupBlockFailure.BackupBlockFailureType.USER_NOT_ALLOWED);
        }

        Path blockPath = this.p2PBackupProperties.getStorageDir().resolve(userId).resolve(backupBlock.getId());
        Mac mac = HmacUtils.getInitializedMac(CryptoConstants.HMAC_BLOCK_ALGORITHM, Base64.getDecoder().decode(backupBlock.getMacSecret()));
        if (Files.exists(blockPath)) {
            try (InputStream fis = Files.newInputStream(blockPath, StandardOpenOption.READ)) {
                HmacUtils.updateHmac(mac, fis);
                String macOfBlock = Base64.getEncoder().encodeToString(mac.doFinal());
                if (!macOfBlock.equals(backupBlock.getMacOfBlock())) {
                    LOGGER.debug("received backup block {} of user {} is already saved", backupBlock.getId(), userId);
                    LOGGER.trace("end saveFromUserInLocalBackup: return {}", BackupBlockFailure.BackupBlockFailureType.BLOCK_ALREADY_SAVED_WITH_OTHER_MAC);
                    return Optional.of(BackupBlockFailure.BackupBlockFailureType.BLOCK_ALREADY_SAVED_WITH_OTHER_MAC);
                } else {
                    LOGGER.debug("block {} already saved", backupBlock.getId());
                    LOGGER.trace("end saveFromUserInLocalBackup: return {}", Optional.empty());
                    return Optional.empty();
                }
            } catch (IOException ioe) {
                LOGGER.warn("unable to save received block {}", backupBlock.getId());
                LOGGER.trace("end saveFromUserInLocalBackup: return {}", BackupBlockFailure.BackupBlockFailureType.GENERAL);
                return Optional.of(BackupBlockFailure.BackupBlockFailureType.GENERAL);
            } finally {
                mac.reset();
            }
        }

        try (ReadableByteChannel downloadChannel = Channels.newChannel(new MacInputStream(new URL(backupBlock.getDownloadURL()).openStream(), mac));
             FileChannel fileChannel = FileChannel.open(blockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            fileChannel.transferFrom(downloadChannel, 0, Long.MAX_VALUE);
        } catch (FileNotFoundException fnfe) {
            LOGGER.info("backup block {} from url {} from user {} not found", backupBlock.getId(), backupBlock.getDownloadURL(), userId);
            LOGGER.trace("end saveFromUserInLocalBackup: return {}", BackupBlockFailure.BackupBlockFailureType.BLOCK_NOT_FOUND);
            return Optional.of(BackupBlockFailure.BackupBlockFailureType.BLOCK_NOT_FOUND);
        } catch (IOException e) {
            LOGGER.warn("unable to save received backup block {} from url {} from user {}", backupBlock.getId(), backupBlock.getDownloadURL(), userId, e);
            this.fileUtils.deleteIfExistsSilent(blockPath);
            LOGGER.trace("end saveFromUserInLocalBackup: return {}", BackupBlockFailure.BackupBlockFailureType.GENERAL);
            return Optional.of(BackupBlockFailure.BackupBlockFailureType.GENERAL);
        }

        // check afterwards if the mac was correct, because this should be the exception
        String macOfBlock = Base64.getEncoder().encodeToString(mac.doFinal());

        if (!macOfBlock.equals(backupBlock.getMacOfBlock())) {
            // the block was modified on the cloud-server
            LOGGER.warn("mac ({}) of received block {} is not equal to expected mac ({})", macOfBlock, backupBlock.getId(), backupBlock.getMacOfBlock());
            this.fileUtils.deleteIfExistsSilent(blockPath);
            LOGGER.trace("end saveFromUserInLocalBackup: return {}", BackupBlockFailure.BackupBlockFailureType.WRONG_MAC);
            return Optional.of(BackupBlockFailure.BackupBlockFailureType.WRONG_MAC);
        } else {
            LOGGER.debug("saved block {}", backupBlock.getId());
            LOGGER.trace("end saveFromUserInLocalBackup: return Optional.empty()");
            return Optional.empty();
        }
    }

    @Override
    public Optional<Path> loadFromLocalStorage(String id) {
        Path blockFile = this.p2PBackupProperties.getStorageDir().resolve(FOLDER_BACKUP).resolve(id);
        if (Files.exists(blockFile)) {
            return Optional.of(blockFile);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Path> loadFromLocalStorage(String userId, String id) {
        Path blockFile = this.p2PBackupProperties.getStorageDir().resolve(userId).resolve(id);
        if (Files.exists(blockFile)) {
            return Optional.of(blockFile);
        }
        return Optional.empty();
    }

    @Override
    public List<Path> loadFromLocalStorageByPrefix(String userId, String prefix) {
        List<Path> files = this.loadAllFromLocalStorage(userId);
        if (CollectionUtils.isEmpty(files)) {
            return new ArrayList<>();
        }
        return files.stream().filter(p -> p.getFileName().toString().startsWith(prefix)).toList();
    }

    private List<Path> loadAllFromLocalStorage(String userId) {
        LOGGER.trace("begin loadAllFromLocalStorage(userId={})", userId);
        List<Path> files = null;
        Path userDir = this.p2PBackupProperties.getStorageDir().resolve(userId);
        if (!Files.isDirectory(userDir)) {
            return new ArrayList<>();
        }
        try (Stream<Path> allPaths = Files.list(userDir)) {
            files = this.applyFileFilter(allPaths).toList();
        } catch (IOException ioe) {
            LOGGER.warn("unable to load blocks for user {}", userId);
            return new ArrayList<>();
        }

        LOGGER.trace("end loadAllFromLocalStorage: return {}", files);
        return files;
    }

    @Override
    public List<String> getBlockIds(String userId) {
        LOGGER.trace("begin getBlockIds");
        List<Path> blocks = this.loadAllFromLocalStorage(userId);
        if (CollectionUtils.isEmpty(blocks)) {
            return new ArrayList<>();
        }
        Path userDir = this.p2PBackupProperties.getStorageDir().resolve(userId);
        List<String> files = blocks.stream().map(p -> userDir.relativize(p).toString()).toList();
        LOGGER.trace("end getBlockIds: return {}", files);
        return files;
    }

    @SneakyThrows
    public List<Path> getFiles() {
        LOGGER.trace("begin getFiles");
        List<Path> files = null;
        try (Stream<Path> allPaths = Files.list(this.p2PBackupProperties.getStorageDir().resolve(FOLDER_BACKUP))) {
            files = this.applyFileFilter(allPaths).toList();
        }
        LOGGER.trace("end getFiles: return {}", files);
        return files;
    }

    private Stream<Path> applyFileFilter(Stream<Path> paths) {
        return paths
                .filter(p -> !p.getFileName().endsWith(FOLDER_BACKUP))
                .filter(p -> !p.getFileName().endsWith(FOLDER_DELETED))
                .filter(Files::isReadable)
                .filter(Files::isRegularFile);
    }
}
