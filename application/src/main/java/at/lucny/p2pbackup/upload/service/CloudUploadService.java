package at.lucny.p2pbackup.upload.service;

import at.lucny.p2pbackup.core.domain.BlockMetaData;
import at.lucny.p2pbackup.core.domain.CloudUpload;

import jakarta.validation.constraints.NotNull;
import java.util.Optional;

public interface CloudUploadService {

    @NotNull CloudUpload saveCloudUpload(@NotNull BlockMetaData bmd, @NotNull String macSecret, @NotNull String mac);

    void removeCloudUploadByBlockMetaDataId(@NotNull String bmdId);

    void removeCloudUpload(@NotNull CloudUpload cloudUpload);

    void uploadLocalBackupBlocks();

    void removeFromCloudStorageService(@NotNull String bmdId);

    @NotNull CloudUpload saveCloudUpload(@NotNull String blockMetaDataId, @NotNull String macSecret, @NotNull String mac);

    @NotNull Optional<CloudUpload> getCloudUpload(@NotNull String bmdId);

    @NotNull Optional<CloudUpload> updateCloudUpload(@NotNull String cloudUploadId, @NotNull String provider, @NotNull String url);
}
