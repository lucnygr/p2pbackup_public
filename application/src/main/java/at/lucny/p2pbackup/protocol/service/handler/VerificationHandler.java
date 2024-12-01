package at.lucny.p2pbackup.protocol.service.handler;

import at.lucny.p2pbackup.localstorage.service.LocalStorageService;
import at.lucny.p2pbackup.network.dto.*;
import at.lucny.p2pbackup.network.service.handler.ClientHandler;
import at.lucny.p2pbackup.network.service.handler.ServerHandler;
import at.lucny.p2pbackup.network.service.listener.SuccessListener;
import at.lucny.p2pbackup.verification.service.VerificationService;
import at.lucny.p2pbackup.verification.service.VerificationValueService;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
@ChannelHandler.Sharable
public class VerificationHandler extends MessageToMessageDecoder<ProtocolMessageWrapper> implements ServerHandler, ClientHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(VerificationHandler.class);

    private final LocalStorageService localStorageService;

    private final VerificationValueService verificationValueService;

    private final VerificationService verificationService;

    public VerificationHandler(LocalStorageService localStorageService, VerificationValueService verificationValueService, VerificationService verificationService) {
        this.localStorageService = localStorageService;
        this.verificationValueService = verificationValueService;
        this.verificationService = verificationService;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ProtocolMessageWrapper msg, List<Object> out) {
        switch (msg.protocolMessage().getMessageCase()) {
            case VERIFYBLOCK -> this.processVerifyBlock(ctx, msg.userId(), msg.protocolMessage().getVerifyBlock());
            case VERIFYBLOCKRESPONSE -> this.processVerifyResponseBlock(ctx, msg.userId(), msg.protocolMessage().getVerifyBlockResponse());
            case VERIFYBLOCKFAILURE -> this.processVerifyFailureBlock(msg.userId(), msg.protocolMessage().getVerifyBlockFailure());
            default -> out.add(msg);
        }
    }

    private void processVerifyBlock(ChannelHandlerContext ctx, String userId, VerifyBlock verifyBlock) {
        Optional<Path> pathToBlockOptional = this.localStorageService.loadFromLocalStorage(userId, verifyBlock.getId());
        if (pathToBlockOptional.isEmpty()) {
            LOGGER.info("block {} of user {} is missing from local storage", verifyBlock.getId(), userId);
            var response = VerifyBlockFailure.newBuilder().setId(verifyBlock.getId()).setType(VerifyBlockFailure.VerifyBlockFailureType.BLOCK_MISSING);
            ProtocolMessage message = ProtocolMessage.newBuilder().setVerifyBlockFailure(response).build();
            ctx.writeAndFlush(message);
            return;
        }
        Path pathToBlock = pathToBlockOptional.get();

        Optional<String> hash = this.verificationValueService.generateHashFromChallenge(pathToBlock, verifyBlock.getVerificationValueId());
        if (hash.isEmpty()) {
            LOGGER.warn("could not calculate hash for block {} of user {}", verifyBlock.getId(), userId);
            var response = VerifyBlockFailure.newBuilder().setId(verifyBlock.getId()).setType(VerifyBlockFailure.VerifyBlockFailureType.GENERAL);
            ProtocolMessage message = ProtocolMessage.newBuilder().setVerifyBlockFailure(response).build();
            ctx.writeAndFlush(message);
        } else {
            String hashValue = hash.get();
            var response = VerifyBlockResponse.newBuilder().setId(verifyBlock.getId()).setVerificationValueId(verifyBlock.getVerificationValueId()).setHash(hashValue);
            ProtocolMessage message = ProtocolMessage.newBuilder().setVerifyBlockResponse(response).build();
            ctx.writeAndFlush(message);
            LOGGER.debug("calculated hash {} for block {}", hashValue, verifyBlock.getId());
        }
    }

    private void processVerifyResponseBlock(ChannelHandlerContext ctx, String userId, VerifyBlockResponse verifyBlockResponse) {
        boolean dataLocationVerified = this.verificationService.verifyHashOfLocation(verifyBlockResponse.getId(), userId, verifyBlockResponse.getVerificationValueId(), verifyBlockResponse.getHash());

        // if the location could not be verified send a delete-message for the block to that location
        if (!dataLocationVerified) {
            LOGGER.debug("sending delete to user {} for block {}", userId, verifyBlockResponse.getId());
            var response = DeleteBlock.newBuilder().addId(verifyBlockResponse.getId());
            ProtocolMessage message = ProtocolMessage.newBuilder().setDeleteBlock(response).build();
            ChannelFuture future = ctx.writeAndFlush(message);

            // after sucessfully sending a delete-message to the user remove the location from the block-locations
            future.addListener(new SuccessListener(() -> this.verificationService.deleteLocationFromBlock(verifyBlockResponse.getId(), userId)));
        }
    }

    private void processVerifyFailureBlock(String userId, VerifyBlockFailure verifyBlockFailure) {
        LOGGER.warn("unable to verify backup block {} on user {}: failure was {}", verifyBlockFailure.getId(), userId, verifyBlockFailure.getType());

        if (verifyBlockFailure.getType() == VerifyBlockFailure.VerifyBlockFailureType.BLOCK_MISSING) {
            this.verificationService.deleteLocationFromBlock(verifyBlockFailure.getId(), userId);
        } else {
            this.verificationService.markLocationUnverified(verifyBlockFailure.getId(), userId);
        }
    }

}
