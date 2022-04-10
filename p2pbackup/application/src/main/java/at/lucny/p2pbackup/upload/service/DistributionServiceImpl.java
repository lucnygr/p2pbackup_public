package at.lucny.p2pbackup.upload.service;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.core.domain.BlockMetaData;
import at.lucny.p2pbackup.core.domain.CloudUpload;
import at.lucny.p2pbackup.core.domain.DataLocation;
import at.lucny.p2pbackup.core.repository.BlockMetaDataRepository;
import at.lucny.p2pbackup.core.repository.CloudUploadRepository;
import at.lucny.p2pbackup.core.repository.DataLocationRepository;
import at.lucny.p2pbackup.network.dto.BackupBlock;
import at.lucny.p2pbackup.network.dto.ProtocolMessage;
import at.lucny.p2pbackup.network.dto.RestoreBlock;
import at.lucny.p2pbackup.network.dto.RestoreBlockFor;
import at.lucny.p2pbackup.network.service.ClientService;
import at.lucny.p2pbackup.network.service.NettyClient;
import at.lucny.p2pbackup.network.service.listener.SuccessListener;
import com.google.common.collect.Lists;
import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Validated
@Service
public class DistributionServiceImpl implements DistributionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributionServiceImpl.class);

    private final CloudUploadRepository cloudUploadRepository;

    private final CloudUploadService cloudUploadService;

    private final BlockMetaDataRepository blockMetaDataRepository;

    private final ClientService clientService;

    private final P2PBackupProperties p2PBackupProperties;

    private final DataLocationRepository dataLocationRepository;

    public DistributionServiceImpl(CloudUploadRepository cloudUploadRepository, CloudUploadService cloudUploadService, BlockMetaDataRepository blockMetaDataRepository, @Lazy ClientService clientService, P2PBackupProperties p2PBackupProperties, DataLocationRepository dataLocationRepository) {
        this.cloudUploadRepository = cloudUploadRepository;
        this.cloudUploadService = cloudUploadService;
        this.blockMetaDataRepository = blockMetaDataRepository;
        this.clientService = clientService;
        this.p2PBackupProperties = p2PBackupProperties;
        this.dataLocationRepository = dataLocationRepository;
    }

    @Override
    public int getNumberOfVerifiedReplicas(BlockMetaData bmd) {
        LocalDateTime verificationIsInvalidDateTime = this.calulateVerificationInvalidDateTime();
        return (int) bmd.getLocations().stream().filter(location -> location.getVerified().isAfter(verificationIsInvalidDateTime)).count();
    }

    @Override
    public LocalDateTime calulateVerificationInvalidDateTime() {
        return LocalDateTime.now(ZoneOffset.UTC).minus(this.p2PBackupProperties.getVerificationProperties().getDurationBeforeVerificationInvalid());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasEnoughVerifiedReplicas(String blockMetaDataId) {
        Optional<BlockMetaData> optionalBlockMetaData = this.blockMetaDataRepository.findByIdFetchLocations(blockMetaDataId);
        if (optionalBlockMetaData.isEmpty()) {
            LOGGER.warn("blockMetaData with id {} not found", blockMetaDataId);
            return true;
        }

        return this.hasEnoughVerifiedReplicas(optionalBlockMetaData.get());
    }

    @Override
    public boolean hasEnoughVerifiedReplicas(BlockMetaData bmd) {
        return this.getNumberOfVerifiedReplicas(bmd) >= this.p2PBackupProperties.getMinimalReplicas();
    }

    @Override
    public void distributeBlocks() {
        LOGGER.trace("begin distributeBlocks()");

        long totalNrOfDistributableBlocks = this.cloudUploadRepository.countByShareUrlIsNotNull();
        if (totalNrOfDistributableBlocks > 0) {
            LOGGER.info("prepare to distribute up to {} blocks", totalNrOfDistributableBlocks);
        }

        Set<CloudUpload> cloudUploadsToDelete = new HashSet<>();
        // try to distribute at maximum 1000 blocks for this iteration
        List<String> cloudUploadIds = this.cloudUploadRepository.findIdByShareUrlIsNotNull(PageRequest.of(0, 1000)).getContent();
        List<List<String>> partitionedCloudUploadIds = Lists.partition(cloudUploadIds, 100);
        long nrOfProcessedUploads = 0;
        long nrOfSuccessfullDistributions = 0;

        for (List<String> nextCloudUploadIds : partitionedCloudUploadIds) {
            List<CloudUpload> cloudUploads = this.cloudUploadRepository.findAllById(nextCloudUploadIds);

            for (CloudUpload cloudUpload : cloudUploads) {
                BlockMetaData bmd = this.blockMetaDataRepository.findByIdFetchLocations(cloudUpload.getBlockMetaData().getId()).orElseThrow(() -> new IllegalStateException("Unable to find BlockMetaData with id " + cloudUpload.getBlockMetaData().getId()));

                boolean enoughVerifiedReplicas = this.hasEnoughVerifiedReplicas(bmd);
                if (enoughVerifiedReplicas) {
                    cloudUploadsToDelete.add(cloudUpload);
                    LOGGER.debug("backup-block {} already has enough replicas", cloudUpload.getBlockMetaData().getId());
                    continue;
                }

                if (this.distributeBlock(cloudUpload, bmd)) {
                    nrOfSuccessfullDistributions++;
                }

                nrOfProcessedUploads++;
                if (nrOfProcessedUploads % 100 == 0) {
                    LOGGER.info("processed {}/{} entries for distribution, {} distributed", nrOfProcessedUploads, totalNrOfDistributableBlocks, nrOfSuccessfullDistributions);
                }
            }
        }

        if (nrOfProcessedUploads > 0) {
            LOGGER.info("processed {}/{} entries for distribution, {} distributed. stop and continue with next run.", nrOfProcessedUploads, totalNrOfDistributableBlocks, nrOfSuccessfullDistributions);
        }

        if (!CollectionUtils.isEmpty(cloudUploadsToDelete)) {
            LOGGER.info("deleting {} cloudUploads with enough replicas", cloudUploadsToDelete.size());
            for (CloudUpload cloudUpload : cloudUploadsToDelete) {
                this.cloudUploadService.removeCloudUpload(cloudUpload);
            }
        }

        LOGGER.trace("end distributeBlocks");
    }

    /**
     * Finds users to distribute the given block to and sends them the BackupBlock-message.
     *
     * @param cloudUpload the cloud-upload-entry for the block with the download-url
     * @param bmd         metadata of the block
     * @return true if the block could be distributed to an other user, otherwise false
     */
    private boolean distributeBlock(CloudUpload cloudUpload, BlockMetaData bmd) {
        List<NettyClient> usersForReplication = this.getUsersForReplication(bmd);
        if (CollectionUtils.isEmpty(usersForReplication)) {
            LOGGER.debug("for backup-block {} are no other users online to backup to", cloudUpload.getBlockMetaData().getId());
            return false;
        }

        var backupBlockBuilder = BackupBlock.newBuilder().setId(bmd.getId()).setDownloadURL(cloudUpload.getShareUrl())
                .setMacOfBlock(cloudUpload.getEncryptedBlockMac()).setMacSecret(cloudUpload.getMacSecret());
        ProtocolMessage message = ProtocolMessage.newBuilder().setBackup(backupBlockBuilder).build();

        for (NettyClient client : usersForReplication) {
            try {
                ChannelFuture future = client.write(message);

                // after sucessfully sending a backup-message to the user add him as unverified location
                future.addListener(new SuccessListener(() -> {
                    Optional<DataLocation> optionalLocation = this.dataLocationRepository.findByBlockMetaDataIdAndUserId(bmd.getId(), client.getUser().getId());
                    if (optionalLocation.isEmpty()) {
                        this.dataLocationRepository.save(new DataLocation(bmd, client.getUser().getId(), LocalDateTime.now(ZoneOffset.UTC).minus(this.p2PBackupProperties.getVerificationProperties().getDurationBeforeVerificationInvalid())));
                    }
                }));
            } catch (RuntimeException e) {
                LOGGER.warn("unable to send backup-block to {}", client.getUser().getId());
                return false;
            }
        }
        return true;
    }

    private List<NettyClient> getUsersForReplication(BlockMetaData bmd) {
        List<NettyClient> onlineUser = this.clientService.getOnlineClients();
        if (onlineUser.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> usersAlreadyReplicatedTo = bmd.getLocations().stream().map(DataLocation::getUserId).collect(Collectors.toSet());
        List<NettyClient> appliableUsersForReplication = new ArrayList<>(onlineUser.stream()
                .filter(client -> client.getUser().isAllowBackupDataToUser())
                .filter(client -> !usersAlreadyReplicatedTo.contains(client.getUser().getId())).toList()); // wrap in new list so we can shuffle ist
        Collections.shuffle(appliableUsersForReplication);

        // return needed amount of clients for replication
        return appliableUsersForReplication.subList(0, Math.min(this.p2PBackupProperties.getMinimalReplicas() - this.getNumberOfVerifiedReplicas(bmd), appliableUsersForReplication.size()));
    }

    private Optional<NettyClient> getUsersForBlockRequest(BlockMetaData bmd) {
        List<NettyClient> onlineUser = this.clientService.getOnlineClients();
        if (onlineUser.isEmpty()) {
            return Optional.empty();
        }

        Set<String> usersAlreadyReplicatedTo = bmd.getLocations().stream().map(DataLocation::getUserId).collect(Collectors.toSet());
        List<NettyClient> usersToRequestDataFrom = new ArrayList<>(onlineUser.stream()
                .filter(client -> usersAlreadyReplicatedTo.contains(client.getUser().getId())).toList()); // wrap in new list so we can shuffle ist

        if (!usersToRequestDataFrom.isEmpty()) {
            Collections.shuffle(usersToRequestDataFrom);
            return Optional.of(usersToRequestDataFrom.get(0));
        }

        return Optional.empty();
    }

    @Override
    @Transactional
    public DataLocation addLocationToBlock(String blockMetaDataId, String userId) {
        Optional<DataLocation> optionalLocation = this.dataLocationRepository.findByBlockMetaDataIdAndUserId(blockMetaDataId, userId);

        if (optionalLocation.isPresent()) {
            optionalLocation.get().setVerified(LocalDateTime.now(ZoneOffset.UTC));
            return optionalLocation.get();
        } else {
            BlockMetaData blockMetaData = this.blockMetaDataRepository.getById(blockMetaDataId);
            return this.dataLocationRepository.save(new DataLocation(blockMetaData, userId, LocalDateTime.now(ZoneOffset.UTC)));
        }
    }

    public boolean hasNotEnoughVerifiedReplicas(String bmdId) {
        return this.blockMetaDataRepository.hasNotEnoughVerifiedReplicas(bmdId, this.p2PBackupProperties.getMinimalReplicas(), this.calulateVerificationInvalidDateTime());
    }

    @Override
    public void verifyEnoughReplicas() {
        LOGGER.trace("begin verifyEnoughReplicas()");

        List<BlockMetaData> blocksWithNotEnoughReplicas = this.blockMetaDataRepository.findBlocksWithNotEnoughVerifiedReplicas(this.p2PBackupProperties.getMinimalReplicas(), this.calulateVerificationInvalidDateTime());
        if (!blocksWithNotEnoughReplicas.isEmpty()) {
            LOGGER.info("found {} blocks with not enough replicas", blocksWithNotEnoughReplicas.size());

            for (BlockMetaData bmd : blocksWithNotEnoughReplicas) {
                LOGGER.debug("requesting block {} because there are not enough verified replicas", bmd.getId());
                var request = RestoreBlock.newBuilder().addId(bmd.getId()).setFor(RestoreBlockFor.REDISTRIBUTION);
                var message = ProtocolMessage.newBuilder().setRestoreBlock(request).build();

                Optional<NettyClient> client = this.getUsersForBlockRequest(bmd);
                client.ifPresent(nettyClient -> nettyClient.write(message));
            }
        }

        LOGGER.trace("end verifyEnoughReplicas");
    }
}
