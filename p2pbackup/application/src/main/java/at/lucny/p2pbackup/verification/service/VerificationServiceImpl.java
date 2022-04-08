package at.lucny.p2pbackup.verification.service;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.backup.support.BackupUtils;
import at.lucny.p2pbackup.core.domain.DataLocation;
import at.lucny.p2pbackup.core.repository.DataLocationRepository;
import at.lucny.p2pbackup.localstorage.service.LocalStorageService;
import at.lucny.p2pbackup.network.dto.*;
import at.lucny.p2pbackup.network.service.ClientService;
import at.lucny.p2pbackup.network.service.NettyClient;
import at.lucny.p2pbackup.network.service.listener.SuccessListener;
import at.lucny.p2pbackup.user.domain.User;
import at.lucny.p2pbackup.verification.domain.ActiveVerificationValue;
import com.google.common.collect.Lists;
import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Validated
@Service
public class VerificationServiceImpl implements VerificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VerificationServiceImpl.class);

    private final ClientService clientService;

    private final VerificationValueService verificationValueService;

    private final DataLocationRepository dataLocationRepository;

    private final LocalStorageService localStorageService;

    private final P2PBackupProperties p2PBackupProperties;

    public VerificationServiceImpl(@Lazy ClientService clientService, VerificationValueService verificationValueService, DataLocationRepository dataLocationRepository, LocalStorageService localStorageService, P2PBackupProperties p2PBackupProperties) {
        this.clientService = clientService;
        this.verificationValueService = verificationValueService;
        this.dataLocationRepository = dataLocationRepository;
        this.localStorageService = localStorageService;
        this.p2PBackupProperties = p2PBackupProperties;
    }

    /**
     * Sends a delete-block message to the given data-location. Removes the data-location afterwards.
     * Returns immediately if the user is not online.
     *
     * @param dataLocation the data-location
     */
    private void sendDeleteBlock(DataLocation dataLocation) {
        if (!this.clientService.isOnline(dataLocation.getUserId())) {
            return;
        }

        var deleteBlockBuilder = DeleteBlock.newBuilder().addId(dataLocation.getBlockMetaData().getId());
        ProtocolMessage message = ProtocolMessage.newBuilder().setDeleteBlock(deleteBlockBuilder).build();
        NettyClient client = this.clientService.getClient(dataLocation.getUserId());

        try {
            ChannelFuture future = client.write(message);

            // after sucessfully sending a delete-message to the user remove the location from the block-locations
            future.addListener(new SuccessListener(() -> this.dataLocationRepository.deleteAllInBatch(Collections.singletonList(dataLocation))));
        } catch (RuntimeException rte) {
            LOGGER.warn("unable to send delete-block to {}", client.getUser().getId());
        }

    }

    @Override
    public void verifyBlocks() {
        LOGGER.trace("start verifyBlocks()");

        List<String> onlineUsers = this.clientService.getOnlineClients().stream().map(NettyClient::getUser).map(User::getId).toList();

        if (CollectionUtils.isEmpty(onlineUsers)) {
            LOGGER.info("no other users online");
            return;
        }

        LOGGER.info("start to verify blocks of other users");

        LOGGER.debug("start to delete blocks from unreliable users");

        LocalDateTime deleteWhereVerifyIsOlderThan = LocalDateTime.now(ZoneOffset.UTC).minus(this.p2PBackupProperties.getVerificationProperties().getDurationBeforeDeletion());
        List<String> dataLocationsToRemove = this.dataLocationRepository.findDataLocationIdsToVerify(deleteWhereVerifyIsOlderThan, onlineUsers, PageRequest.of(0, 10000)).getContent();
        List<List<String>> partitionedDataLocationsToRemove = Lists.partition(dataLocationsToRemove, 100);

        for (List<String> dataLocationIds : partitionedDataLocationsToRemove) {
            Page<DataLocation> unreliableDataLocations = this.dataLocationRepository.findByIdIn(dataLocationIds, Pageable.unpaged());

            for (DataLocation dataLocation : unreliableDataLocations.getContent()) {
                this.sendDeleteBlock(dataLocation);
            }
        }

        LocalDateTime verifyOlderThan = LocalDateTime.now(ZoneOffset.UTC).minus(this.p2PBackupProperties.getVerificationProperties().getDurationBetweenVerifications());
        LOGGER.debug("start to verify blocks that where verified before {}", verifyOlderThan);
        List<String> dataLocationsToVerify = new ArrayList<>(this.dataLocationRepository.findDataLocationIdsToVerify(verifyOlderThan, onlineUsers, PageRequest.of(0, 10000)).getContent());
        dataLocationsToVerify.removeAll(dataLocationsToRemove); // remove locations that should be deleted anyway
        List<List<String>> partitionedDataLocationsToVerify = Lists.partition(dataLocationsToVerify, 100);

        for (List<String> dataLocationIds : partitionedDataLocationsToVerify) {
            Page<DataLocation> dataLocations = this.dataLocationRepository.findByIdIn(dataLocationIds, Pageable.unpaged());

            for (DataLocation dataLocation : dataLocations.getContent()) {
                this.verifyDataLocation(dataLocation);
            }
        }

        LOGGER.info("finished verifying blocks of other users");

        LOGGER.trace("end verifyBlocks");
    }

    /**
     * Tries to verify the given data location by loading the active verification-value and sending a VerifyBlock-request to the user.
     * Returns immediately if the user is not online.
     * If no verification-values are available requests the block from an online user and returns.
     *
     * @param dataLocation the DataLocation to verify
     */
    private void verifyDataLocation(DataLocation dataLocation) {
        if (!this.clientService.isOnline(dataLocation.getUserId())) {
            return;
        }

        String bmdId = dataLocation.getBlockMetaData().getId();
        Optional<ActiveVerificationValue> optionalVerificationValue = this.verificationValueService.getOrRenewActiveVerificationValue(bmdId);

        // no more verification values available
        if (optionalVerificationValue.isEmpty()) {
            Optional<Path> optionalPathToBlock = this.localStorageService.loadFromLocalStorage(bmdId);
            // if the block is in the local storage, try to generate new verification values
            if (optionalPathToBlock.isPresent()) {
                try {
                    byte[] data = Files.readAllBytes(optionalPathToBlock.get());
                    this.verificationValueService.ensureVerificationValues(bmdId, ByteBuffer.wrap(data));
                    optionalVerificationValue = this.verificationValueService.getOrRenewActiveVerificationValue(bmdId);
                } catch (IOException ioe) {
                    LOGGER.warn("unable to read data of local block {}", optionalPathToBlock.get());
                }
            }
        }

        // if the we still have no verification values we have to request the block from a different user
        if (optionalVerificationValue.isEmpty()) {
            this.requestBlockForVerification(bmdId);
            return;
        }

        var verifyBlock = VerifyBlock.newBuilder().setId(bmdId).setVerificationValueId(optionalVerificationValue.get().getId());
        ProtocolMessage message = ProtocolMessage.newBuilder().setVerifyBlock(verifyBlock).build();

        NettyClient client = this.clientService.getClient(dataLocation.getUserId());
        try {
            client.write(message);
        } catch (RuntimeException e) {
            LOGGER.warn("unable to send verify-block to {}", client.getUser().getId());
        }
    }

    /**
     * Sends a RestoreBlock to an online user for generating verification-values for that block.
     *
     * @param bmdId the id of the block
     */
    private void requestBlockForVerification(String bmdId) {
        List<String> storingUserIdsOfBlock = this.dataLocationRepository.findByBlockMetaDataId(bmdId).stream().map(DataLocation::getUserId).toList();
        List<NettyClient> onlineClientsThatStoreBlock = this.clientService.getOnlineClients(storingUserIdsOfBlock);

        if (CollectionUtils.isEmpty(onlineClientsThatStoreBlock)) {
            return;
        }

        // pick a user at random to request the block
        NettyClient client = onlineClientsThatStoreBlock.get(BackupUtils.RANDOM.nextInt(onlineClientsThatStoreBlock.size()));

        var restoreBlock = RestoreBlock.newBuilder().addId(bmdId).setFor(RestoreBlockFor.VERIFICATION);
        ProtocolMessage message = ProtocolMessage.newBuilder().setRestoreBlock(restoreBlock).build();

        try {
            client.write(message);
        } catch (RuntimeException e) {
            LOGGER.warn("unable to send verify-block to {}", client.getUser().getId());
        }
    }

    @Transactional
    @Override
    public void deleteLocationFromBlock(String blockMetaDataId, String userId) {
        Optional<DataLocation> optionalDataLocation = this.dataLocationRepository.findByBlockMetaDataIdAndUserId(blockMetaDataId, userId);
        if (optionalDataLocation.isEmpty()) {
            LOGGER.debug("data-location for block {} and user {} does not exist", blockMetaDataId, userId);
            return;
        }
        this.dataLocationRepository.delete(optionalDataLocation.get());
    }


    @Transactional
    @Override
    public void markLocationUnverified(String blockMetaDataId, String userId) {
        Optional<DataLocation> optionalDataLocation = this.dataLocationRepository.findByBlockMetaDataIdAndUserId(blockMetaDataId, userId);
        if (optionalDataLocation.isEmpty()) {
            LOGGER.debug("data-location for block {} and user {} does not exist", blockMetaDataId, userId);
            return;
        }
        LocalDateTime dateForLocationUnverified = LocalDateTime.now(ZoneOffset.UTC).minus(this.p2PBackupProperties.getVerificationProperties().getDurationBeforeVerificationInvalid());
        if (dateForLocationUnverified.isBefore(optionalDataLocation.get().getVerified())) {
            optionalDataLocation.get().setVerified(dateForLocationUnverified);
        }
    }

    @Transactional
    @Override
    public boolean verifyHashOfLocation(String blockMetaDataId, String userId, String verificationValueId, String
            hash) {
        Optional<DataLocation> optionalLocation = this.dataLocationRepository.findByBlockMetaDataIdAndUserId(blockMetaDataId, userId);
        if (optionalLocation.isEmpty()) {
            LOGGER.debug("user {} does not save the block {}", userId, blockMetaDataId);
            return false;
        }

        Optional<ActiveVerificationValue> verificationValue = this.verificationValueService.getActiveVerificationValue(blockMetaDataId);
        // do nothing if verification value no longer exists - should be checked automatically with a new verification processing
        if (verificationValue.isEmpty()) {
            LOGGER.info("verification value for block {} with challenge {} no longer exists", blockMetaDataId, verificationValueId);
            return true;
        }

        if (!verificationValue.get().getId().equals(verificationValueId)) {
            LOGGER.info("verification value for block {} with challenge {} no longer exists", blockMetaDataId, verificationValueId);
            return true;
        }

        if (!verificationValue.get().getHash().equals(hash)) {
            LOGGER.info("user {} failed the verification of the block {}", userId, blockMetaDataId);
            optionalLocation.get().setVerified(LocalDateTime.now(ZoneOffset.UTC).minus(this.p2PBackupProperties.getVerificationProperties().getDurationBeforeVerificationInvalid()));
            return false;
        }

        optionalLocation.get().setVerified(LocalDateTime.now(ZoneOffset.UTC));
        return true;
    }

    @Override
    public void markLocationVerified(String blockMetaDataId, String userId) {
        Optional<DataLocation> optionalDataLocation = this.dataLocationRepository.findByBlockMetaDataIdAndUserId(blockMetaDataId, userId);
        if (optionalDataLocation.isEmpty()) {
            LOGGER.debug("data-location for block {} and user {} does not exist", blockMetaDataId, userId);
            return;
        }
        optionalDataLocation.get().setVerified(LocalDateTime.now(ZoneOffset.UTC));
    }
}
