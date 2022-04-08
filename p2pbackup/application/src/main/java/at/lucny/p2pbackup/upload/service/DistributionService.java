package at.lucny.p2pbackup.upload.service;

import at.lucny.p2pbackup.core.domain.BlockMetaData;
import at.lucny.p2pbackup.core.domain.DataLocation;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

public interface DistributionService {

    /**
     * Iterates over all distributable blocks that where uploaded to the cloud-provider and distributes them to other peers.
     */
    void distributeBlocks();

    int getNumberOfVerifiedReplicas(@NotNull BlockMetaData bmd);

    boolean hasEnoughVerifiedReplicas(@NotNull String blockMetaDataId);

    boolean hasEnoughVerifiedReplicas(@NotNull BlockMetaData bmd);

    DataLocation addLocationToBlock(@NotNull String blockMetaDataId, @NotNull String userId);

    @NotNull LocalDateTime calulateVerificationInvalidDateTime();

    void verifyEnoughReplicas();
}
