package at.lucny.p2pbackup.user.service;

import at.lucny.p2pbackup.core.support.CertificateUtils;
import at.lucny.p2pbackup.core.support.UserInputHelper;
import at.lucny.p2pbackup.user.domain.NetworkAddress;
import at.lucny.p2pbackup.user.domain.User;
import at.lucny.p2pbackup.user.repository.UserRepository;
import at.lucny.p2pbackup.user.support.UserAddedEvent;
import at.lucny.p2pbackup.user.support.UserChangedEvent;
import at.lucny.p2pbackup.user.support.UserDeletedEvent;
import lombok.SneakyThrows;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Validated
public class UserServiceImpl implements UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);

    private final CertificateUtils certificateUtils = new CertificateUtils();

    private final UserRepository userRepository;

    private final ApplicationEventPublisher applicationEventPublisher;

    public UserServiceImpl(UserRepository userRepository, ApplicationEventPublisher applicationEventPublisher) {
        this.userRepository = userRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    @SneakyThrows
    @Transactional
    public void addUser(String userId, String host, int port, Path pathToCertificate, boolean allowBackupDataFromUser, boolean allowBackupDataToUser, boolean checkSHAFingerprintOfCertificate) {
        Optional<User> userEntity = this.userRepository.findById(userId);
        if (userEntity.isPresent()) {
            LOGGER.info("User {} already imported", userId);
            return;
        }

        Optional<byte[]> certificate = this.loadCertificate(userId, pathToCertificate, checkSHAFingerprintOfCertificate);

        if (certificate.isPresent()) {
            User user = new User(userId, certificate.get(), allowBackupDataFromUser, allowBackupDataToUser, new NetworkAddress(host, port));
            user = this.userRepository.save(user);

            this.applicationEventPublisher.publishEvent(new UserAddedEvent(this, userId));

            LOGGER.info("User {} saved", user.getId());
        }
    }

    @Override
    @SneakyThrows
    @Transactional
    public void changeCertificate(String userId, Path pathToCertificate) {
        Optional<User> userEntity = this.userRepository.findById(userId);
        if (userEntity.isEmpty()) {
            LOGGER.info("User {} does not exist", userId);
            return;
        }
        User user = userEntity.get();

        Optional<byte[]> certificate = this.loadCertificate(userId, pathToCertificate, true);

        if (certificate.isPresent()) {
            user.setCertificate(certificate.get());

            this.applicationEventPublisher.publishEvent(new UserChangedEvent(this, userId));

            LOGGER.info("Users {} certificate changed", user.getId());
        }
    }

    @Override
    @SneakyThrows
    @Transactional
    public void changePermissions(String userId, Boolean allowBackupDataFromUser, Boolean allowBackupDataToUser) {
        Optional<User> userEntity = this.userRepository.findById(userId);
        if (userEntity.isEmpty()) {
            LOGGER.info("User {} does not exist", userId);
            return;
        }
        User user = userEntity.get();

        user.setAllowBackupDataFromUser(allowBackupDataFromUser);
        user.setAllowBackupDataToUser(allowBackupDataToUser);

        this.applicationEventPublisher.publishEvent(new UserChangedEvent(this, userId));

        LOGGER.info("Users {} permissions changed", user.getId());
    }

    private Optional<byte[]> loadCertificate(String userId, Path pathToCertificate, boolean checkSHAFingerprintOfCertificate) throws IOException, CertificateEncodingException {
        byte[] certificate = Files.readAllBytes(pathToCertificate);
        X509Certificate x509Certificate = this.certificateUtils.readCertificate(certificate);
        String commonName = this.certificateUtils.getCommonName(x509Certificate);
        if (!commonName.equals(userId)) { // format CN=name
            throw new IllegalArgumentException("registered userId " + userId + " does not match certificate issuers common name " + x509Certificate.getIssuerX500Principal().getName());
        }

        if (checkSHAFingerprintOfCertificate) {
            String sha256Fingerprint = DigestUtils.sha256Hex(x509Certificate.getEncoded());
            String sha512Fingerprint = DigestUtils.sha3_512Hex(x509Certificate.getEncoded());

            String yesNo = new UserInputHelper().read("Are the fingerprints\nSHA-256 fingerprint " + sha256Fingerprint + "\nSHA3-512 fingerprint " + sha512Fingerprint + "\n for the certificate of user " + userId + " correct? (Y/N):");
            if (!"Y".equalsIgnoreCase(yesNo)) {
                LOGGER.info("Fingerprint not accepted, abort importing user {}", userId);
                return Optional.empty();
            }
        }
        return Optional.of(certificate);
    }

    @Override
    @Transactional
    public void deleteUser(String userId) {
        Optional<User> userEntity = this.userRepository.findById(userId);
        if (userEntity.isEmpty()) {
            LOGGER.info("User {} does not exist", userId);
            return;
        }

        this.userRepository.delete(userEntity.get());

        this.applicationEventPublisher.publishEvent(new UserDeletedEvent(this, userId));

        LOGGER.info("User {} deleted", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findUser(String userId) {
        Optional<User> user = this.userRepository.findById(userId);
        if (user.isPresent()) {
            this.userRepository.fetchAddresses(Collections.singletonList(userId));
        }
        return user;
    }

    public List<User> findAllUsers() {
        return this.userRepository.findAll();
    }
}


