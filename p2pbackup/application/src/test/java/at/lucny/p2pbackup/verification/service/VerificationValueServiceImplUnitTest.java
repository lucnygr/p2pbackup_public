package at.lucny.p2pbackup.verification.service;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.core.domain.BlockMetaData;
import at.lucny.p2pbackup.core.repository.BlockMetaDataRepository;
import at.lucny.p2pbackup.core.support.CryptoUtils;
import at.lucny.p2pbackup.verification.domain.ActiveVerificationValue;
import at.lucny.p2pbackup.verification.domain.VerificationValue;
import at.lucny.p2pbackup.verification.repository.ActiveVerificationValueRepository;
import at.lucny.p2pbackup.verification.repository.VerificationValueRepository;
import com.google.common.collect.Lists;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationValueServiceImplUnitTest {

    private VerificationValueServiceImpl verificationService;

    @Mock
    private VerificationValueRepository verificationValueRepositoryMock;

    @Mock
    private ActiveVerificationValueRepository activeVerificationValueRepository;

    @Mock
    private BlockMetaDataRepository blockMetaDataRepositoryMock;

    private P2PBackupProperties p2PBackupProperties;

    private CryptoUtils cryptoUtils;

    @BeforeEach
    void beforeEach() throws NoSuchAlgorithmException, NoSuchProviderException {
        this.cryptoUtils = new CryptoUtils();
        this.p2PBackupProperties = new P2PBackupProperties();
        this.verificationService = spy(new VerificationValueServiceImpl(this.verificationValueRepositoryMock, this.activeVerificationValueRepository, this.blockMetaDataRepositoryMock, this.p2PBackupProperties, this.cryptoUtils));
    }

    @Test
    void testNeedsGenerationOfVerificationValues() {
        when(this.verificationValueRepositoryMock.countByBlockMetaDataId("ID")).thenReturn(7L);
        boolean result = this.verificationService.needsGenerationOfVerificationValues("ID");
        assertThat(result).isFalse();
    }

    @Test
    void testNeedsGenerationOfVerificationValues_notEnoughVerificationValues() {
        when(this.verificationValueRepositoryMock.countByBlockMetaDataId("ID")).thenReturn(6L);
        boolean result = this.verificationService.needsGenerationOfVerificationValues("ID");
        assertThat(result).isTrue();
    }

    @Test
    void testEnsureVerificationValues_enoughValuesAvailable() {
        ByteBuffer data = ByteBuffer.wrap("My Testdata".getBytes(StandardCharsets.UTF_8));
        when(this.verificationValueRepositoryMock.countByBlockMetaDataId("ID")).thenReturn(12L);

        this.verificationService.ensureVerificationValues("ID", data);

        verifyNoMoreInteractions(this.verificationValueRepositoryMock, this.blockMetaDataRepositoryMock);
    }

    @Test
    void testEnsureVerificationValues_needsAdditionalVerificationValues() {
        ByteBuffer data = ByteBuffer.wrap("My Testdata".getBytes(StandardCharsets.UTF_8));
        when(this.verificationValueRepositoryMock.countByBlockMetaDataId("ID")).thenReturn(0L);
        BlockMetaData bmd = new BlockMetaData();
        when(this.blockMetaDataRepositoryMock.getById("ID")).thenReturn(bmd);

        this.verificationService.ensureVerificationValues("ID", data);

        ArgumentCaptor<List<VerificationValue>> vvCaptor = ArgumentCaptor.forClass(List.class);
        verify(this.verificationValueRepositoryMock).saveAll(vvCaptor.capture());
        assertThat(vvCaptor.getValue()).hasSize(12);
        for (VerificationValue value : vvCaptor.getValue()) {
            assertThat(value.getId()).isNotNull().isBase64();
            assertThat(value.getBlockMetaData()).isEqualTo(bmd);
            assertThat(value.getHash()).isNotNull().isBase64();
            assertThat(Base64.getDecoder().decode(value.getHash())).isEqualTo(DigestUtils.sha3_512(value.getId() + "My Testdata"));
        }
        verifyNoMoreInteractions(this.verificationValueRepositoryMock, this.blockMetaDataRepositoryMock);
    }

    @Test
    void testGenerateHashFromChallenge_fileDoesNotExist(@TempDir Path tempDir) {
        Path tempFile = tempDir.resolve("test.txt");

        Optional<String> hash = this.verificationService.generateHashFromChallenge(tempFile, "CHALLENGE");
        assertThat(hash).isNotNull().isNotPresent();
    }

    @Test
    void testGenerateHashFromChallenge(@TempDir Path tempDir) throws IOException {
        Path tempFile = tempDir.resolve("test.txt");
        Files.writeString(tempFile, "My Testdata");

        Optional<String> hash = this.verificationService.generateHashFromChallenge(tempFile, "CHALLENGE");
        assertThat(hash).isNotNull().isPresent();
        assertThat(hash.get()).isBase64();
        assertThat(Base64.getDecoder().decode(hash.get())).isEqualTo(DigestUtils.sha3_512("CHALLENGEMy Testdata"));
    }

    @Test
    void testGetActiveVerificationValue_noValueAvailable() {
        when(this.activeVerificationValueRepository.findByBlockMetaDataId("ID")).thenReturn(Optional.empty());

        Optional<ActiveVerificationValue> result = this.verificationService.getActiveVerificationValue("ID");
        assertThat(result).isNotNull().isNotPresent();

        verifyNoMoreInteractions(this.verificationValueRepositoryMock, this.activeVerificationValueRepository);
    }

    @Test
    void testGetActiveVerificationValue_verificationValueActive() {
        ActiveVerificationValue verificationValue = new ActiveVerificationValue("ID", new BlockMetaData(), "HASH", LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        when(this.activeVerificationValueRepository.findByBlockMetaDataId("ID")).thenReturn(Optional.of(verificationValue));

        Optional<ActiveVerificationValue> result = this.verificationService.getActiveVerificationValue("ID");

        assertThat(result).isNotNull().isPresent().contains(verificationValue);

        verifyNoMoreInteractions(this.verificationValueRepositoryMock, this.activeVerificationValueRepository);
    }

    @Test
    void testGetActiveVerificationValue_verificationValueActive_butNoLongerValid() {
        ActiveVerificationValue verificationValue = new ActiveVerificationValue("ID", new BlockMetaData(), "HASH", LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        when(this.activeVerificationValueRepository.findByBlockMetaDataId("ID")).thenReturn(Optional.of(verificationValue));

        Optional<ActiveVerificationValue> result = this.verificationService.getActiveVerificationValue("ID");
        assertThat(result).isNotNull().isNotPresent();

        verifyNoMoreInteractions(this.verificationValueRepositoryMock, this.activeVerificationValueRepository);
    }

    @Test
    void testGetOrRenewActiveVerificationValue_verificationValueActive() {
        BlockMetaData bmd = new BlockMetaData();
        ActiveVerificationValue verificationValue = new ActiveVerificationValue("ID", bmd, "HASH", LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        when(this.activeVerificationValueRepository.findByBlockMetaDataId("ID")).thenReturn(Optional.of(verificationValue));

        Optional<ActiveVerificationValue> result = this.verificationService.getOrRenewActiveVerificationValue("ID");
        assertThat(result).isNotNull().isPresent().contains(verificationValue);

        verifyNoMoreInteractions(this.verificationValueRepositoryMock, this.activeVerificationValueRepository);
    }

    @Test
    void testGetOrRenewActiveVerificationValue_noActiveVerificationValue_noValuesAvailable() {
        when(this.activeVerificationValueRepository.findByBlockMetaDataId("ID")).thenReturn(Optional.empty());
        when(this.verificationValueRepositoryMock.findByBlockMetaDataIdOrderByIdAsc("ID")).thenReturn(new ArrayList<>());

        Optional<ActiveVerificationValue> result = this.verificationService.getOrRenewActiveVerificationValue("ID");
        assertThat(result).isNotNull().isNotPresent();

        verifyNoMoreInteractions(this.verificationValueRepositoryMock, this.activeVerificationValueRepository);
    }

    @Test
    void testGetOrRenewActiveVerificationValue_verificationValueActive_butNoLongerValid_noValuesAvailable() {
        BlockMetaData bmd = new BlockMetaData();
        ActiveVerificationValue verificationValue = new ActiveVerificationValue("ID", bmd, "HASH", LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        when(this.activeVerificationValueRepository.findByBlockMetaDataId("ID")).thenReturn(Optional.of(verificationValue));
        when(this.verificationValueRepositoryMock.findByBlockMetaDataIdOrderByIdAsc("ID")).thenReturn(new ArrayList<>());

        Optional<ActiveVerificationValue> result = this.verificationService.getOrRenewActiveVerificationValue("ID");
        assertThat(result).isNotNull().isNotPresent();

        verify(this.activeVerificationValueRepository).flush();
        verify(this.activeVerificationValueRepository).deleteAllInBatch(List.of(verificationValue));
        verifyNoMoreInteractions(this.verificationValueRepositoryMock, this.activeVerificationValueRepository);
    }

    @Test
    void testGetOrRenewActiveVerificationValue_noActiveVerificationValue_chooseAndReturnVerificationValue() {
        when(this.activeVerificationValueRepository.findByBlockMetaDataId("ID")).thenReturn(Optional.empty());
        BlockMetaData bmd = new BlockMetaData();
        VerificationValue verificationValue1 = new VerificationValue("id1", bmd, "HASH");
        VerificationValue verificationValue2 = new VerificationValue("id2", bmd, "HASH2");
        when(this.verificationValueRepositoryMock.findByBlockMetaDataIdOrderByIdAsc("ID")).thenReturn(Lists.newArrayList(verificationValue1, verificationValue2));

        ActiveVerificationValue expectedValue = new ActiveVerificationValue(verificationValue1, LocalDateTime.now());
        when(this.activeVerificationValueRepository.save(any(ActiveVerificationValue.class))).thenReturn(expectedValue);

        Optional<ActiveVerificationValue> result = this.verificationService.getOrRenewActiveVerificationValue("ID");
        assertThat(result).isNotNull().isPresent().contains(expectedValue);

        ArgumentCaptor<ActiveVerificationValue> avvCaptor = ArgumentCaptor.forClass(ActiveVerificationValue.class);
        verify(this.activeVerificationValueRepository).save(avvCaptor.capture());
        assertThat(avvCaptor.getValue().getId()).isEqualTo(verificationValue1.getId());
        assertThat(avvCaptor.getValue().getBlockMetaData().getId()).isEqualTo(verificationValue1.getBlockMetaData().getId());
        assertThat(avvCaptor.getValue().getHash()).isEqualTo(verificationValue1.getHash());
        LocalDateTime nextVerify = LocalDateTime.now(ZoneOffset.UTC).plus(this.p2PBackupProperties.getVerificationProperties().getDurationBetweenVerifications());
        assertThat(avvCaptor.getValue().getActiveUntil()).isNotNull().isBetween(nextVerify.minusSeconds(10), nextVerify.plusSeconds(10));

        verify(this.verificationValueRepositoryMock).delete(verificationValue1);
        verifyNoMoreInteractions(this.verificationValueRepositoryMock);
    }

    @Test
    void testGetVerificationValue() {
        BlockMetaData bmd = new BlockMetaData();
        VerificationValue verificationValue1 = new VerificationValue("id1", bmd, "HASH");
        when(this.verificationValueRepositoryMock.findById("ID")).thenReturn(Optional.of(verificationValue1));

        Optional<VerificationValue> result = this.verificationService.getVerificationValue("ID");
        assertThat(result).isNotNull().isPresent().contains(verificationValue1);

        verifyNoMoreInteractions(this.verificationValueRepositoryMock);
    }

}
