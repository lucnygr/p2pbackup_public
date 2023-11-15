package at.lucny.p2pbackup.verification.service;

import at.lucny.p2pbackup.application.config.VerificationProperties;

import jakarta.validation.constraints.NotNull;

public interface VerificationService {

    /**
     * Verifies all blocks with a verification-timeout. Blocks with to much time without a successfull verification get deleted from other users.
     */
    void verifyBlocks();

    void deleteLocationFromBlock(@NotNull String blockMetaDataId, @NotNull String userId);

    /**
     * Marks the data-location of the block at the given user as verified by setting the verified-date to NOW.
     *
     * @param blockMetaDataId the id of the block
     * @param userId          the saving user
     */
    void markLocationVerified(@NotNull String blockMetaDataId, @NotNull String userId);

    /**
     * Marks the data-location of the block at the given user as unverified by setting the verified-date to NOW - {@link VerificationProperties#getDurationBeforeVerificationInvalid()}.
     *
     * @param blockMetaDataId the id of the block
     * @param userId          the saving user
     */
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

    /**
     * Marks all data-locations of the user for verification by setting the verified-date to NOW - {@link VerificationProperties#getDurationBetweenVerifications()}.
     * The locations still count as valid, but this triggers verification-requests for all blocks.
     *
     * @param userId the id of the user
     */
    void markLocationsForVerification(@NotNull String userId);
}
