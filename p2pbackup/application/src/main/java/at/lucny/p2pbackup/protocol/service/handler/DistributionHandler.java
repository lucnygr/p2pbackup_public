package at.lucny.p2pbackup.protocol.service.handler;

import at.lucny.p2pbackup.localstorage.service.LocalStorageService;
import at.lucny.p2pbackup.network.dto.*;
import at.lucny.p2pbackup.network.service.handler.ClientHandler;
import at.lucny.p2pbackup.network.service.handler.ServerHandler;
import at.lucny.p2pbackup.upload.service.CloudUploadService;
import at.lucny.p2pbackup.upload.service.DistributionService;
import at.lucny.p2pbackup.verification.service.VerificationService;
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

    private final VerificationService verificationService;

    public DistributionHandler(CloudUploadService cloudUploadService, LocalStorageService localStorageService, DistributionService distributionService, VerificationService verificationService) {
        this.cloudUploadService = cloudUploadService;
        this.localStorageService = localStorageService;
        this.distributionService = distributionService;
        this.verificationService = verificationService;
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
        LOGGER.trace("block {} has enough backup locations: {}", backupBlockSuccess.getId(), enoughBackupLocationsExist);
        if (enoughBackupLocationsExist) {
            LOGGER.debug("delete block {} from cloud-upload", backupBlockSuccess.getId());
            this.cloudUploadService.removeCloudUploadByBlockMetaDataId(backupBlockSuccess.getId());
        }
    }

    private void processBackupBlockFailure(String userId, BackupBlockFailure backupBlockFailure) {
        LOGGER.debug("unable to save backup block {} on user {}: failure was {}", backupBlockFailure.getId(), userId, backupBlockFailure.getType());
        if (backupBlockFailure.getType() == BackupBlockFailure.BackupBlockFailureType.WRONG_MAC) { // restart cloud upload
            LOGGER.debug("removing block {} from cloud-storage", backupBlockFailure.getId());
            this.verificationService.deleteLocationFromBlock(backupBlockFailure.getId(), userId);
            this.cloudUploadService.removeFromCloudStorageService(backupBlockFailure.getId());
        } else if (backupBlockFailure.getType() == BackupBlockFailure.BackupBlockFailureType.USER_NOT_ALLOWED) { // log warning to change user settings
            LOGGER.warn("user {} does not allow storage of backups", userId);
            this.verificationService.deleteLocationFromBlock(backupBlockFailure.getId(), userId);
        } else if (backupBlockFailure.getType() == BackupBlockFailure.BackupBlockFailureType.BLOCK_NOT_FOUND && this.distributionService.hasEnoughVerifiedReplicas(backupBlockFailure.getId())) {
            // if the block could not be saved and there are enough replicas delete all unverified locations
            LOGGER.debug("delete datalocation {} from block {}", userId, backupBlockFailure.getId());
            this.verificationService.deleteLocationFromBlock(backupBlockFailure.getId(), userId);
        } else if (backupBlockFailure.getType() == BackupBlockFailure.BackupBlockFailureType.BLOCK_ALREADY_SAVED_WITH_OTHER_MAC) { // trigger verify in case the block differs from the saved one
            LOGGER.debug("mark datalocation {} from block {} as unverified", userId, backupBlockFailure.getId());
            this.verificationService.markLocationUnverified(backupBlockFailure.getId(), userId);
        } else {
            LOGGER.warn("unable to save backup block {} on user {}: failure was {}", backupBlockFailure.getId(), userId, backupBlockFailure.getType());
        }
    }

    private void processDeleteBlock(String userId, DeleteBlock deleteBlock) {
        LOGGER.debug("deleting blocks {} on user {}", deleteBlock.getIdList(), userId);
        this.localStorageService.removeFromLocalStorage(userId, new ArrayList<>(deleteBlock.getIdList()));
    }
}
