package at.lucny.p2pbackup.restore.service;

import jakarta.validation.constraints.NotNull;
import java.util.concurrent.CompletableFuture;

public interface RestoreCloudUploadService {
    @NotNull CompletableFuture<Void> recoverFromCloudStorages();
}
