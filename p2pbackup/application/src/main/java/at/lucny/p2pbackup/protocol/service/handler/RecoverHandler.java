package at.lucny.p2pbackup.protocol.service.handler;

import at.lucny.p2pbackup.backup.support.BackupConstants;
import at.lucny.p2pbackup.core.service.BlockEncryptionService;
import at.lucny.p2pbackup.core.service.ByteBufferPoolService;
import at.lucny.p2pbackup.localstorage.service.LocalStorageService;
import at.lucny.p2pbackup.network.dto.ProtocolMessage;
import at.lucny.p2pbackup.network.dto.ProtocolMessageWrapper;
import at.lucny.p2pbackup.network.dto.RecoverBackupIndexResponse;
import at.lucny.p2pbackup.network.dto.RecoverBlocksResponse;
import at.lucny.p2pbackup.network.service.handler.ClientHandler;
import at.lucny.p2pbackup.network.service.handler.ServerHandler;
import at.lucny.p2pbackup.restore.service.RecoveryService;
import at.lucny.p2pbackup.verification.service.VerificationService;
import at.lucny.p2pbackup.verification.service.VerificationValueService;
import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
@ChannelHandler.Sharable
public class RecoverHandler extends MessageToMessageDecoder<ProtocolMessageWrapper> implements ServerHandler, ClientHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecoverHandler.class);

    private final LocalStorageService localStorageService;

    private final ByteBufferPoolService byteBufferPoolService;

    private final BlockEncryptionService blockEncryptionService;

    private final VerificationService verificationService;

    private final VerificationValueService verificationValueService;

    private final RecoveryService recoveryService;

    public RecoverHandler(LocalStorageService localStorageService, ByteBufferPoolService byteBufferPoolService, BlockEncryptionService blockEncryptionService, VerificationService verificationService, VerificationValueService verificationValueService, RecoveryService recoveryService) {
        this.localStorageService = localStorageService;
        this.byteBufferPoolService = byteBufferPoolService;
        this.blockEncryptionService = blockEncryptionService;
        this.verificationService = verificationService;
        this.verificationValueService = verificationValueService;
        this.recoveryService = recoveryService;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ProtocolMessageWrapper msg, List<Object> out) {
        switch (msg.protocolMessage().getMessageCase()) {
            case RECOVERBACKUPINDEX -> this.processRecoverBackupIndex(ctx, msg.userId());
            case RECOVERBACKUPINDEXREPONSE -> this.processRecoverBackupIndexResponse(msg.userId(), msg.protocolMessage().getRecoverBackupIndexReponse());
            case RECOVERBLOCKSRESPONSE -> this.processRecoverBlocksResponse(msg.userId(), msg.protocolMessage().getRecoverBlocksResponse());
            default -> out.add(msg);
        }
    }

    private void processRecoverBackupIndex(ChannelHandlerContext ctx, String userId) {
        LOGGER.debug("recovering backup index for user {}", userId);
        List<String> blockIds = this.localStorageService.getBlockIds(userId);
        var response = RecoverBlocksResponse.newBuilder().addAllBlockIds(blockIds);
        ctx.write(ProtocolMessage.newBuilder().setRecoverBlocksResponse(response).build());

        List<Path> backupIndexBlocks = this.localStorageService.loadFromLocalStorageByPrefix(userId, BackupConstants.BACKUP_INDEX_BLOCK_PREFIX);
        Optional<Path> pathToLatestBackupIndexBlock = backupIndexBlocks.stream().max(Comparator.comparing(p -> p.getFileName().toString()));
        if (pathToLatestBackupIndexBlock.isPresent()) {
            try (InputStream is = Files.newInputStream(pathToLatestBackupIndexBlock.get(), StandardOpenOption.READ)) {
                ByteString data = ByteString.readFrom(is);
                var backupIndexResponse = RecoverBackupIndexResponse.newBuilder().setLatestBackupIndexId(pathToLatestBackupIndexBlock.get().getFileName().toString()).setLatestBackupIndex(data);
                ctx.write(ProtocolMessage.newBuilder().setRecoverBackupIndexReponse(backupIndexResponse).build());
            } catch (IOException e) {
                LOGGER.warn("could not get backup-index-block {} to recover for user {}", pathToLatestBackupIndexBlock.get(), userId, e);
            }
        } else {
            LOGGER.info("no backup-index-block for user {} found", userId);
        }

        LOGGER.debug("found backup-index-block {} and {} blocks and send to user {}", pathToLatestBackupIndexBlock, blockIds.size(), userId);
        ctx.flush();
    }

    private void processRecoverBackupIndexResponse(String userId, RecoverBackupIndexResponse recoverBackupIndexResponse) {
        LOGGER.debug("recovering backup-index-block {} from user {}", recoverBackupIndexResponse.getLatestBackupIndexId(), userId);

        if (!recoverBackupIndexResponse.getLatestBackupIndex().isEmpty()) {
            Integer key = this.byteBufferPoolService.calculateBufferSize(recoverBackupIndexResponse.getLatestBackupIndex().size());
            ByteBuffer decryptedDataBuffer = this.byteBufferPoolService.borrowObject(key);

            try {
                // decrypt the block-content, check integrity and write it into the decryptedDataBuffer
                this.blockEncryptionService.decrypt(recoverBackupIndexResponse.getLatestBackupIndex().asReadOnlyByteBuffer(), recoverBackupIndexResponse.getLatestBackupIndexId().getBytes(StandardCharsets.UTF_8), decryptedDataBuffer);

                // save found version of backupIndex
                this.recoveryService.recoverBackupIndex(userId, decryptedDataBuffer);

                // mark location of backupIndex as verified and generate verification values for block
                this.verificationService.markLocationVerified(recoverBackupIndexResponse.getLatestBackupIndexId(), userId);
                this.verificationValueService.ensureVerificationValues(recoverBackupIndexResponse.getLatestBackupIndexId(), recoverBackupIndexResponse.getLatestBackupIndex().asReadOnlyByteBuffer());
            } catch (IllegalStateException e) {
                LOGGER.warn("unable to restore backup-index-block {} from user {} due to exception", recoverBackupIndexResponse.getLatestBackupIndexId(), userId, e);
                this.verificationService.markLocationUnverified(recoverBackupIndexResponse.getLatestBackupIndexId(), userId);
            } finally {
                this.byteBufferPoolService.returnObject(key, decryptedDataBuffer);
            }
        } else {
            LOGGER.info("user {} does not save a backup-index", userId);
        }
    }

    private void processRecoverBlocksResponse(String userId, RecoverBlocksResponse recoverBlocksResponse) {
        LOGGER.debug("recovering {} blocks from user {}", recoverBlocksResponse.getBlockIdsList(), userId);

        this.recoveryService.recoverBlockMetaData(userId, new HashSet<>(recoverBlocksResponse.getBlockIdsList()));
    }
}
