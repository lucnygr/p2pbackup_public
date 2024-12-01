package at.lucny.p2pbackup.user.service;

import at.lucny.p2pbackup.user.domain.User;
import jakarta.validation.Valid;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.util.Optional;

public interface UserService {

    void addUser(@NotNull String userId, @NotNull String host, int port, @NotNull Path pathToCertificate, boolean allowBackupDataFromUser, boolean allowBackupDataToUser, boolean checkSHAFingerprintOfCertificate);

    void changeCertificate(@NotNull String userId, @NotNull Path pathToCertificate);

    void changePermissions(@NotNull String userId, @NotNull Boolean allowBackupDataFromUser, @NotNull Boolean allowBackupDataToUser);

    void deleteUser(@NotNull String userId);

    @NotNull @Valid Optional<User> findUser(@NotNull String userId);

}
