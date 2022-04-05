package at.lucny.p2pbackup.upload.service;

import at.lucny.p2pbackup.core.domain.BlockMetaData;
import at.lucny.p2pbackup.core.domain.DataLocation;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

public interface DistributionService {

    void distributeBlocks();

    int getNumberOfVerifiedReplicas(@NotNull BlockMetaData bmd);

    boolean hasEnoughVerifiedReplicas(@NotNull String blockMetaDataId);

    boolean hasEnoughVerifiedReplicas(@NotNull BlockMetaData bmd);

    DataLocation addLocationToBlock(@NotNull String blockMetaDataId, @NotNull String userId);

    @NotNull LocalDateTime calulateVerificationInvalidDateTime();

    void verifyEnoughReplicas();
}
