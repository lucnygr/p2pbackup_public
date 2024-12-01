package at.lucny.p2pbackup.test.integration;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.core.domain.BlockMetaData;
import at.lucny.p2pbackup.core.repository.BlockMetaDataRepository;
import at.lucny.p2pbackup.verification.domain.ActiveVerificationValue;
import at.lucny.p2pbackup.verification.domain.VerificationValue;
import at.lucny.p2pbackup.verification.repository.ActiveVerificationValueRepository;
import at.lucny.p2pbackup.verification.repository.VerificationValueRepository;
import at.lucny.p2pbackup.verification.service.VerificationValueService;
import at.lucny.p2pbackup.verification.service.VerificationValueServiceImpl;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles({"integrationtest"})
@ContextConfiguration(initializers = BaseSingleApplicationIntegrationTest.ConfigDirAndRootDirectoryContextInitializer.class)
class VerificationValueServiceIntegrationTest extends BaseSingleApplicationIntegrationTest {

    @Autowired
    private P2PBackupProperties p2PBackupProperties;

    @Autowired
    private VerificationValueService verificationValueService;

    @Autowired
    private BlockMetaDataRepository blockMetaDataRepository;

    @Autowired
    private VerificationValueRepository verificationValueRepository;

    @Autowired
    private ActiveVerificationValueRepository activeVerificationValueRepository;

    @Value("classpath:testfile1.txt")
    private Resource testfile1;

    @Value("classpath:testfile3.txt")
    private Resource testfile3;

    private BlockMetaData bmd;

    @BeforeEach
    void beforeEach() {
        BlockMetaData bmd = new BlockMetaData("hash");
        this.bmd = this.blockMetaDataRepository.save(bmd);
    }

    @AfterEach
    void afterEach() {
        this.activeVerificationValueRepository.deleteAll();
        this.verificationValueRepository.deleteAll();
        this.blockMetaDataRepository.deleteAll();
    }

    @Test
    void testEnsureVerificationValues() throws IOException {
        ByteBuffer data = ByteBuffer.wrap(this.readFile(this.testfile1.getFile().toPath()));
        this.verificationValueService.ensureVerificationValues(bmd.getId(), data);

        List<VerificationValue> values = this.verificationValueRepository.findAll();
        assertThat(values).hasSize(12);
        for (VerificationValue value : values) {
            assertThat(value.getId()).isNotNull();
            assertThat(value.getBlockMetaData().getId()).isEqualTo(bmd.getId());
            assertThat(value.getHash()).isEqualTo(Base64.getEncoder().encodeToString(DigestUtils.sha3_512(new SequenceInputStream(new ByteArrayInputStream(value.getId().getBytes(StandardCharsets.UTF_8)), this.testfile1.getInputStream()))));
        }
    }

    @Test
    void testEnsureVerificationValues_enoughVerificationValues() throws IOException {
        List<VerificationValue> verificationValues = new ArrayList<>();
        for (int i = 0; i < VerificationValueServiceImpl.NR_OF_VERIFICATION_VALUES; i++) {
            verificationValues.add(this.verificationValueRepository.save(new VerificationValue("id" + i, bmd, "hash" + i)));
        }

        ByteBuffer data = ByteBuffer.wrap(this.readFile(this.testfile1.getFile().toPath()));
        this.verificationValueService.ensureVerificationValues(bmd.getId(), data);

        List<VerificationValue> values = this.verificationValueRepository.findAll();
        assertThat(values).containsExactlyInAnyOrderElementsOf(verificationValues);
    }

    @ParameterizedTest
    @CsvSource({"CHALLENGE,n/CXV+eKhq5oMeKKjPxOgUyWfJgHyrjGuUcAHnL80E17QZpsQFCvOk8ybfS0lKon5XPJ6D5A1+3HHLKabIACNQ==",
            "CHALLENGE2,s5T9ZXx0iDF4DgNDXtmHwDJmdT+59b9QX7rn6zLa2PXjlPQGqeo8SNxXOZsLN/gpGihv/WwZ8CU7+Rz1IaunlQ==",
            "ZUFALLS_CHALLENGE,L9V/W/BvHClgi1MSz8NBbVz1n3J7T7aPFBm30hSDWiMNwra+kEfO3qVQTfbPBOzjqZ1L8FvmIAhAuW1Qj1vGGg==",
            "_SUPER_SECRET_,p5m1971Y0zp9E2moxQGie6/YIwO4Dz6UnAb8jDoYy87yS4IjeYPTbWLoSK+3oBBhw+0/DF8dzxABSIBJqn/FjA=="})
    void testGenerateHashFromChallenge(String challenge, String expectedHash) throws IOException {
        Optional<String> hash = this.verificationValueService.generateHashFromChallenge(this.testfile3.getFile().toPath(), challenge);
        assertThat(hash).isPresent().contains(expectedHash);
    }

    @Test
    void testGetVerificationValue() {
        VerificationValue value = this.verificationValueRepository.save(new VerificationValue("id1", bmd, "hash"));

        Optional<VerificationValue> vv = this.verificationValueService.getVerificationValue(value.getId());
        assertThat(vv).isPresent().contains(value);
    }

    @Test
    void testGetActiveVerificationValue_verificationValueActive() {
        ActiveVerificationValue verificationValue = new ActiveVerificationValue("ID", bmd, "HASH", LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        verificationValue = this.activeVerificationValueRepository.save(verificationValue);

        Optional<ActiveVerificationValue> result = this.verificationValueService.getActiveVerificationValue(this.bmd.getId());
        assertThat(result).isNotNull().isPresent().contains(verificationValue);
    }

    @Test
    void testGetActiveVerificationValue_verificationValueActive_butNoLongerValid() {
        ActiveVerificationValue verificationValue = new ActiveVerificationValue("ID", bmd, "HASH", LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        this.activeVerificationValueRepository.save(verificationValue);

        Optional<ActiveVerificationValue> result = this.verificationValueService.getActiveVerificationValue(this.bmd.getId());
        assertThat(result).isNotNull().isNotPresent();

        assertThat(this.activeVerificationValueRepository.count()).isOne();
    }

    @Test
    void testGetOrRenewActiveVerificationValue_verificationValueActive() {
        ActiveVerificationValue verificationValue = new ActiveVerificationValue("ID", bmd, "HASH", LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        verificationValue = this.activeVerificationValueRepository.save(verificationValue);

        Optional<ActiveVerificationValue> result = this.verificationValueService.getOrRenewActiveVerificationValue(this.bmd.getId());
        assertThat(result).isNotNull().isPresent().contains(verificationValue);
    }

    @Test
    void testGetOrRenewActiveVerificationValue_verificationValueActive_butNoLongerValid_noValuesAvailable() {
        ActiveVerificationValue verificationValue = new ActiveVerificationValue("ID", bmd, "HASH", LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        this.activeVerificationValueRepository.save(verificationValue);

        Optional<ActiveVerificationValue> result = this.verificationValueService.getOrRenewActiveVerificationValue(this.bmd.getId());
        assertThat(result).isNotNull().isNotPresent();

        assertThat(this.activeVerificationValueRepository.count()).isZero();
        assertThat(this.verificationValueRepository.count()).isZero();
    }

    @Test
    void testGetActiveVerificationValue_noActiveVerificationValue_chooseAndReturnVerificationValue() {
        List<VerificationValue> values = new ArrayList<>();
        VerificationValue verificationValue1 = new VerificationValue("id1", this.bmd, "hash1");
        values.add(this.verificationValueRepository.save(verificationValue1));
        VerificationValue verificationValue2 = new VerificationValue("id2", this.bmd, "hash2");
        values.add(this.verificationValueRepository.save(verificationValue2));
        values.sort(Comparator.comparing(VerificationValue::getId));

        Optional<ActiveVerificationValue> result = this.verificationValueService.getOrRenewActiveVerificationValue(this.bmd.getId());

        assertThat(result).isNotNull().isPresent();
        assertThat(result.get().getId()).isEqualTo(values.get(0).getId());
        assertThat(result.get().getHash()).isEqualTo(values.get(0).getHash());
        assertThat(result.get().getBlockMetaData().getId()).isEqualTo(values.get(0).getBlockMetaData().getId());
        assertThat(result.get().getActiveUntil()).isBetween(LocalDateTime.now(ZoneOffset.UTC).plus(this.p2PBackupProperties.getVerificationProperties().getDurationBetweenVerifications()).minusMinutes(1), LocalDateTime.now(ZoneOffset.UTC).plus(this.p2PBackupProperties.getVerificationProperties().getDurationBetweenVerifications()).plusMinutes(1));

        List<ActiveVerificationValue> activeValues = this.activeVerificationValueRepository.findAll();
        assertThat(activeValues).isNotNull().containsExactly(result.get());

        List<VerificationValue> remainingValues = this.verificationValueRepository.findAll();
        assertThat(remainingValues).isNotNull().containsExactly(values.get(1));
    }

    @Test
    void testGetActiveVerificationValue_activeVerificationValueInvalid_chooseAndReturnVerificationValue() {
        ActiveVerificationValue verificationValue = new ActiveVerificationValue("ID", bmd, "HASH", LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        this.activeVerificationValueRepository.save(verificationValue);

        List<VerificationValue> values = new ArrayList<>();
        VerificationValue verificationValue1 = new VerificationValue("id1", this.bmd, "hash1");
        values.add(this.verificationValueRepository.save(verificationValue1));
        VerificationValue verificationValue2 = new VerificationValue("id2", this.bmd, "hash2");
        values.add(this.verificationValueRepository.save(verificationValue2));
        values.sort(Comparator.comparing(VerificationValue::getId));

        Optional<ActiveVerificationValue> result = this.verificationValueService.getOrRenewActiveVerificationValue(this.bmd.getId());

        assertThat(result).isNotNull().isPresent();
        assertThat(result.get().getId()).isEqualTo(values.get(0).getId());
        assertThat(result.get().getHash()).isEqualTo(values.get(0).getHash());
        assertThat(result.get().getBlockMetaData().getId()).isEqualTo(values.get(0).getBlockMetaData().getId());
        assertThat(result.get().getActiveUntil()).isBetween(LocalDateTime.now(ZoneOffset.UTC).plus(this.p2PBackupProperties.getVerificationProperties().getDurationBetweenVerifications()).minusMinutes(1), LocalDateTime.now(ZoneOffset.UTC).plus(this.p2PBackupProperties.getVerificationProperties().getDurationBetweenVerifications()).plusMinutes(1));

        List<ActiveVerificationValue> activeValues = this.activeVerificationValueRepository.findAll();
        assertThat(activeValues).isNotNull().containsExactly(result.get());

        List<VerificationValue> remainingValues = this.verificationValueRepository.findAll();
        assertThat(remainingValues).isNotNull().containsExactly(values.get(1));
    }


}
