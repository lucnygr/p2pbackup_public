package at.lucny.p2pbackup.protocol.service.handler;

import at.lucny.p2pbackup.core.service.BlockEncryptionService;
import at.lucny.p2pbackup.localstorage.dto.LocalStorageEntry;
import at.lucny.p2pbackup.localstorage.service.LocalStorageService;
import at.lucny.p2pbackup.network.dto.*;
import at.lucny.p2pbackup.network.service.handler.ClientHandler;
import at.lucny.p2pbackup.network.service.handler.ServerHandler;
import at.lucny.p2pbackup.restore.service.RestorationService;
import at.lucny.p2pbackup.upload.service.CloudUploadService;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;

@Service
@ChannelHandler.Sharable
public class RestoreHandler extends MessageToMessageDecoder<ProtocolMessageWrapper> implements ServerHandler, ClientHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestoreHandler.class);

    private final LocalStorageService localStorageService;

    private final BlockEncryptionService blockEncryptionService;

    private final VerificationService verificationService;

    private final VerificationValueService verificationValueService;

    private final RestorationService restorationService;

    private final CloudUploadService cloudUploadService;

    public RestoreHandler(LocalStorageService localStorageService, BlockEncryptionService blockEncryptionService, VerificationService verificationService, VerificationValueService verificationValueService, RestorationService restorationService, CloudUploadService cloudUploadService) {
        this.localStorageService = localStorageService;
        this.blockEncryptionService = blockEncryptionService;
        this.verificationService = verificationService;
        this.verificationValueService = verificationValueService;
        this.restorationService = restorationService;
        this.cloudUploadService = cloudUploadService;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ProtocolMessageWrapper msg, List<Object> out) {
        switch (msg.protocolMessage().getMessageCase()) {
            case RESTOREBLOCK -> this.processRestoreBlock(ctx, msg.userId(), msg.protocolMessage().getRestoreBlock());
            case RESTOREBLOCKRESPONSE -> this.processRestoreBlockResponse(msg.userId(), msg.protocolMessage().getRestoreBlockResponse());
            case RESTOREBLOCKFAILURE -> this.processRestoreBlockFailure(msg.userId(), msg.protocolMessage().getRestoreBlockFailure());
            default -> out.add(msg);
        }
    }

    private void processRestoreBlock(ChannelHandlerContext ctx, String userId, RestoreBlock restoreBlock) {
        LOGGER.debug("received restore block {} from user {}", restoreBlock.getIdList(), userId);

        for (String id : restoreBlock.getIdList()) {
            Optional<Path> pathToBlock = this.localStorageService.loadFromLocalStorage(userId, id);
            if (pathToBlock.isPresent()) {
                try (InputStream is = Files.newInputStream(pathToBlock.get(), StandardOpenOption.READ)) {
                    ByteString data = ByteString.readFrom(is);
                    var response = RestoreBlockResponse.newBuilder().setId(id).setData(data).setFor(restoreBlock.getFor());
                    ProtocolMessage message = ProtocolMessage.newBuilder().setRestoreBlockResponse(response).build();
                    ctx.writeAndFlush(message);
                    LOGGER.debug("sent block {} to restore to user {}", id, userId);
                } catch (IOException e) {
                    LOGGER.warn("could not get block {} to restore to user {}", id, userId, e);
                    var response = RestoreBlockFailure.newBuilder().setId(id).setType(RestoreBlockFailure.RestoreBlockFailureType.GENERAL);
                    ProtocolMessage message = ProtocolMessage.newBuilder().setRestoreBlockFailure(response).build();
                    ctx.writeAndFlush(message);
                }
            } else {
                LOGGER.warn("could not get block {} to restore to user {}", id, userId);
                var response = RestoreBlockFailure.newBuilder().setId(id).setType(RestoreBlockFailure.RestoreBlockFailureType.BLOCK_MISSING);
                ProtocolMessage message = ProtocolMessage.newBuilder().setRestoreBlockFailure(response).build();
                ctx.writeAndFlush(message);
            }
        }
    }

    private void processRestoreBlockResponse(String userId, RestoreBlockResponse restoreBlockResponse) {
        LOGGER.debug("restoring block {} from user {} with cause {}", restoreBlockResponse.getId(), userId, restoreBlockResponse.getFor());

        try {
            switch (restoreBlockResponse.getFor()) {
                case RESTORE -> {
                    LOGGER.debug("restoration of block {}", restoreBlockResponse.getId());
                    this.restorationService.restoreBlock(userId, restoreBlockResponse.getId(), restoreBlockResponse.getData().asReadOnlyByteBuffer());
                }
                case REDISTRIBUTION -> {
                    LOGGER.debug("redistribution of block {}", restoreBlockResponse.getId());
                    this.blockEncryptionService.verify(restoreBlockResponse.getData().asReadOnlyByteBuffer(), restoreBlockResponse.getId().getBytes(StandardCharsets.UTF_8));
                    LocalStorageEntry localStorageEntry = this.localStorageService.saveInLocalStorage(restoreBlockResponse.getId(), restoreBlockResponse.getData().asReadOnlyByteBuffer());
                    this.cloudUploadService.saveCloudUpload(restoreBlockResponse.getId(), localStorageEntry.macSecret(), localStorageEntry.mac());
                }
                default -> {
                    this.blockEncryptionService.verify(restoreBlockResponse.getData().asReadOnlyByteBuffer(), restoreBlockResponse.getId().getBytes(StandardCharsets.UTF_8));
                    LOGGER.trace("received block {} from user {} for {}", restoreBlockResponse.getId(), userId, restoreBlockResponse.getFor());
                }
            }

            // mark location as verified and generate verification values for block
            this.verificationService.markLocationVerified(restoreBlockResponse.getId(), userId);
            this.verificationValueService.ensureVerificationValues(restoreBlockResponse.getId(), restoreBlockResponse.getData().asReadOnlyByteBuffer());
        } catch (IllegalStateException e) {
            LOGGER.warn("unable to restore block {} from user {} due to exception", restoreBlockResponse.getId(), userId, e);
            this.verificationService.markLocationUnverified(restoreBlockResponse.getId(), userId);
        }
    }

    private void processRestoreBlockFailure(String userId, RestoreBlockFailure restoreBlockFailure) {
        LOGGER.warn("unable to restore backup block {} on user {}: failure was {}", restoreBlockFailure.getId(), userId, restoreBlockFailure.getType());

        if (restoreBlockFailure.getType() == RestoreBlockFailure.RestoreBlockFailureType.BLOCK_MISSING) {
            this.verificationService.deleteLocationFromBlock(restoreBlockFailure.getId(), userId);
        } else {
            this.verificationService.markLocationUnverified(restoreBlockFailure.getId(), userId);
        }
    }
}
