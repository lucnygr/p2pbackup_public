package at.lucny.p2pbackup.verification.service;

import javax.validation.constraints.NotNull;

public interface VerificationService {

    void verifyBlocks();

    void deleteLocationFromBlock(@NotNull String blockMetaDataId, @NotNull String userId);

    void markLocationVerified(@NotNull String blockMetaDataId, @NotNull String userId);

    void markLocationUnverified(@NotNull String blockMetaDataId, @NotNull String userId);

    /**
     * Checks if the calculated hash is equal to the expected one. If so sets the backup-location of the given user as verified.
     *
     * @param blockMetaDataId the id of the block
     * @param userId          the user-id of the user that should be verified
     * @param challenge       the challenge of the verification value
     * @param hash            the returned hash of the user
     * @return if the user is a valid backup-location for the block, otherwise false
     */
    boolean verifyHashOfLocation(@NotNull String blockMetaDataId, @NotNull String userId, @NotNull String challenge, @NotNull String hash);
}
