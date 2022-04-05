package at.lucny.p2pbackup.verification.service;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.core.domain.BlockMetaData;
import at.lucny.p2pbackup.core.repository.BlockMetaDataRepository;
import at.lucny.p2pbackup.core.support.CryptoUtils;
import at.lucny.p2pbackup.verification.domain.ActiveVerificationValue;
import at.lucny.p2pbackup.verification.domain.VerificationValue;
import at.lucny.p2pbackup.verification.repository.ActiveVerificationValueRepository;
import at.lucny.p2pbackup.verification.repository.VerificationValueRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@Validated
public class VerificationValueServiceImpl implements VerificationValueService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VerificationValueServiceImpl.class);

    public static final int NR_OF_VERIFICATION_VALUES = 12;

    private final VerificationValueRepository verificationValueRepository;

    private final ActiveVerificationValueRepository activeVerificationValueRepository;

    private final BlockMetaDataRepository blockMetaDataRepository;

    private final P2PBackupProperties p2PBackupProperties;

    private final CryptoUtils cryptoUtils;

    public VerificationValueServiceImpl(VerificationValueRepository verificationValueRepository, ActiveVerificationValueRepository activeVerificationValueRepository, BlockMetaDataRepository blockMetaDataRepository, P2PBackupProperties p2PBackupProperties, CryptoUtils cryptoUtils) {
        this.verificationValueRepository = verificationValueRepository;
        this.activeVerificationValueRepository = activeVerificationValueRepository;
        this.blockMetaDataRepository = blockMetaDataRepository;
        this.p2PBackupProperties = p2PBackupProperties;
        this.cryptoUtils = cryptoUtils;
    }

    @Override
    public boolean needsGenerationOfVerificationValues(String blockMetaDataId) {
        long count = this.verificationValueRepository.countByBlockMetaDataId(blockMetaDataId);
        return count <= NR_OF_VERIFICATION_VALUES / 2;
    }

    @Transactional
    @Override
    public void ensureVerificationValues(String blockMetaDataId, ByteBuffer data) {
        long count = this.verificationValueRepository.countByBlockMetaDataId(blockMetaDataId);
        if (count < NR_OF_VERIFICATION_VALUES) {
            LOGGER.debug("only {} verification values available for block-meta-data {}", count, blockMetaDataId);

            BlockMetaData bmd = this.blockMetaDataRepository.getById(blockMetaDataId);
            List<VerificationValue> values = new ArrayList<>();
            MessageDigest digest = DigestUtils.getSha3_512Digest();

            ByteBuffer bufferToUse = data.duplicate();
            bufferToUse.mark();

            for (long i = count; i < NR_OF_VERIFICATION_VALUES; i++) {
                String id = Base64.getEncoder().encodeToString(this.cryptoUtils.nextBytes(16));
                VerificationValue vv = new VerificationValue(id, bmd);
                vv.setHash(this.generateHashFromChallenge(digest, bufferToUse, vv.getId()));
                digest.reset();
                bufferToUse.reset();

                values.add(vv);
            }

            this.verificationValueRepository.saveAll(values);
        }
    }

    String generateHashFromChallenge(MessageDigest digest, ByteBuffer data, String challenge) {
        digest.update(challenge.getBytes(StandardCharsets.UTF_8));
        digest.update(data);
        byte[] hash = digest.digest();
        return Base64.getEncoder().encodeToString(hash);
    }

    @Override
    public Optional<String> generateHashFromChallenge(Path filePath, String challenge) {
        MessageDigest digest = DigestUtils.getSha3_512Digest();
        digest.update(challenge.getBytes(StandardCharsets.UTF_8));
        try {
            byte[] hash = DigestUtils.digest(digest, filePath, StandardOpenOption.READ);
            return Optional.of(Base64.getEncoder().encodeToString(hash));
        } catch (IOException ioe) {
            LOGGER.warn("unable to calculate hash with challenge {} for file {}", challenge, filePath);
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<ActiveVerificationValue> getActiveVerificationValue(String blockMetaDataId) {
        Optional<ActiveVerificationValue> optionalVerificationValue = this.activeVerificationValueRepository.findByBlockMetaDataId(blockMetaDataId);
        if (optionalVerificationValue.isPresent() && LocalDateTime.now(ZoneOffset.UTC).isBefore(optionalVerificationValue.get().getActiveUntil())) {
            return optionalVerificationValue;
        }

        return Optional.empty();
    }

    @Transactional
    @Override
    public Optional<ActiveVerificationValue> getOrRenewActiveVerificationValue(String blockMetaDataId) {
        Optional<ActiveVerificationValue> optionalVerificationValue = this.activeVerificationValueRepository.findByBlockMetaDataId(blockMetaDataId);
        if (optionalVerificationValue.isPresent()) {
            if (LocalDateTime.now(ZoneOffset.UTC).isBefore(optionalVerificationValue.get().getActiveUntil())) {
                return optionalVerificationValue;
            }
            // if the verification value is no longer valid delete it
            LOGGER.debug("active-verification-value {} for block {} is timed out", optionalVerificationValue.get().getId(), blockMetaDataId);
            this.activeVerificationValueRepository.deleteAllInBatch(List.of(optionalVerificationValue.get()));
        }

        List<VerificationValue> verificationValues = this.verificationValueRepository.findByBlockMetaDataIdOrderByIdAsc(blockMetaDataId);
        if (CollectionUtils.isEmpty(verificationValues)) {
            // there are currently no verification values for the given block-id
            return Optional.empty();
        }

        // pick first verification value, set it as active and return it
        VerificationValue newVerificationValue = verificationValues.get(0);
        ActiveVerificationValue activeVerificationValue = new ActiveVerificationValue(newVerificationValue, LocalDateTime.now(ZoneOffset.UTC).plus(this.p2PBackupProperties.getVerificationProperties().getDurationBetweenVerifications()));
        activeVerificationValue = this.activeVerificationValueRepository.save(activeVerificationValue);
        this.verificationValueRepository.delete(newVerificationValue);
        return Optional.of(activeVerificationValue);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<VerificationValue> getVerificationValue(String id) {
        return this.verificationValueRepository.findById(id);
    }
}
