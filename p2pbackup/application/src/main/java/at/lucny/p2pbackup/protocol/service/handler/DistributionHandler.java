package at.lucny.p2pbackup.protocol.service.handler;

import at.lucny.p2pbackup.localstorage.service.LocalStorageService;
import at.lucny.p2pbackup.network.dto.*;
import at.lucny.p2pbackup.network.service.handler.ClientHandler;
import at.lucny.p2pbackup.network.service.handler.ServerHandler;
import at.lucny.p2pbackup.upload.service.CloudUploadService;
import at.lucny.p2pbackup.upload.service.DistributionService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@ChannelHandler.Sharable
public class DistributionHandler extends MessageToMessageDecoder<ProtocolMessageWrapper> implements ServerHandler, ClientHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributionHandler.class);

    private final CloudUploadService cloudUploadService;

    private final LocalStorageService localStorageService;

    private final DistributionService distributionService;

    public DistributionHandler(CloudUploadService cloudUploadService, LocalStorageService localStorageService, DistributionService distributionService) {
        this.cloudUploadService = cloudUploadService;
        this.localStorageService = localStorageService;
        this.distributionService = distributionService;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ProtocolMessageWrapper msg, List<Object> out) {
        switch (msg.protocolMessage().getMessageCase()) {
            case BACKUP -> this.processBackupBlock(ctx, msg.userId(), msg.protocolMessage().getBackup());
            case BACKUPSUCCESS -> this.processBackupBlockSuccess(msg.userId(), msg.protocolMessage().getBackupSuccess());
            case BACKUPFAILURE -> this.processBackupBlockFailure(msg.userId(), msg.protocolMessage().getBackupFailure());
            case DELETEBLOCK -> this.processDeleteBlock(msg.userId(), msg.protocolMessage().getDeleteBlock());
            default -> out.add(msg);
        }
    }

    private void processBackupBlock(ChannelHandlerContext ctx, String userId, BackupBlock backupBlock) {
        var type = this.localStorageService.saveFromUserInLocalStorage(userId, backupBlock);

        if (type.isPresent()) {
            // unable to save remote backup block
            LOGGER.warn("unable to save backup-block {} from user {}", backupBlock.getId(), userId);
            var blockFailure = BackupBlockFailure.newBuilder().setId(backupBlock.getId()).setType(type.get());
            ProtocolMessage message = ProtocolMessage.newBuilder().setBackupFailure(blockFailure).build();
            ctx.writeAndFlush(message);
        } else {
            var blockSuccess = BackupBlockSuccess.newBuilder().setId(backupBlock.getId());
            ProtocolMessage message = ProtocolMessage.newBuilder().setBackupSuccess(blockSuccess).build();
            ctx.writeAndFlush(message);
            LOGGER.debug("saved block {} from user {}", backupBlock.getId(), userId);
        }
    }

    private void processBackupBlockSuccess(String userId, BackupBlockSuccess backupBlockSuccess) {
        LOGGER.debug("saved block {} on user {}", backupBlockSuccess.getId(), userId);
        this.distributionService.addLocationToBlock(backupBlockSuccess.getId(), userId);
        boolean enoughBackupLocationsExist = this.distributionService.hasEnoughVerifiedReplicas(backupBlockSuccess.getId());
        if (enoughBackupLocationsExist) {
            this.cloudUploadService.removeCloudUploadByBlockMetaDataId(backupBlockSuccess.getId());
        }
    }

    private void processBackupBlockFailure(String userId, BackupBlockFailure backupBlockFailure) {
        LOGGER.warn("unable to save backup block {} on user {}: failure was {}", backupBlockFailure.getId(), userId, backupBlockFailure.getType());
        if (backupBlockFailure.getType() == BackupBlockFailure.BackupBlockFailureType.WRONG_MAC) {
            this.cloudUploadService.removeFromCloudStorageService(backupBlockFailure.getId());
        } else if (backupBlockFailure.getType() == BackupBlockFailure.BackupBlockFailureType.USER_NOT_ALLOWED) {
            LOGGER.warn("user {} does not allow storage of backups", userId);
        }
        // TODO react to saved block with wrong hash
    }

    private void processDeleteBlock(String userId, DeleteBlock deleteBlock) {
        LOGGER.debug("deleting blocks {} on user {}", deleteBlock.getIdList(), userId);
        this.localStorageService.removeFromLocalStorage(userId, new ArrayList<>(deleteBlock.getIdList()));
    }
}
