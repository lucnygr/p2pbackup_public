package at.lucny.p2pbackup.verification.service;

import at.lucny.p2pbackup.verification.domain.ActiveVerificationValue;
import at.lucny.p2pbackup.verification.domain.VerificationValue;

import jakarta.validation.constraints.NotNull;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Optional;

public interface VerificationValueService {

    /**
     * Checks if the given BlockMetaData needs more verification-values. Returns only true when half or less of the required verification-values are present.
     *
     * @param blockMetaDataId the id of the block-meta-data
     * @return true if new verification-values for the block should be calculated
     */
    boolean needsGenerationOfVerificationValues(@NotNull String blockMetaDataId);

    void ensureVerificationValues(@NotNull String blockMetaDataId, @NotNull ByteBuffer data);

    @NotNull Optional<VerificationValue> getVerificationValue(@NotNull String id);

    @NotNull Optional<String> generateHashFromChallenge(@NotNull Path filePath, @NotNull String challenge);

    /**
     * Return the currently set active-verification-value.
     *
     * @param blockMetaDataId the block-id for which a verification value is needed
     * @return the currently active-verification-value or an empty optional if none is set or the active-verification-value is no longer valid.
     */
    @NotNull Optional<ActiveVerificationValue> getActiveVerificationValue(@NotNull String blockMetaDataId);

    /**
     * Return the currently active-verification-value. If the found verification-value is no longer active:
     * <li> this active-verification-value gets deleted
     * <li> a new verification-value gets chosen and persisted as active-verification-value
     * <li> this new verification-value is returned
     *
     * @param blockMetaDataId the block-id for which a verification value is needed
     * @return the currently active-verification-value or an empty optional if there are no more verification-values available.
     */
    Optional<ActiveVerificationValue> getOrRenewActiveVerificationValue(String blockMetaDataId);
}
