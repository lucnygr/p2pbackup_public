package at.lucny.p2pbackup.verification.domain;

import at.lucny.p2pbackup.core.domain.BlockMetaData;
import lombok.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "ACTIVE_VERIFICATION_VALUE", uniqueConstraints = @UniqueConstraint(name = "uc_active_verification_value_block_meta_data_id", columnNames = {"BLOCK_META_DATA_ID"}))
@Getter
@Setter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ActiveVerificationValue extends AbstractVerificationValue {

    @Column(name = "ACTIVE_UNTIL", nullable = false)
    private LocalDateTime activeUntil;

    public ActiveVerificationValue(String id, BlockMetaData blockMetaData, String hash, LocalDateTime activeUntil) {
        super(id, blockMetaData, hash);
        this.activeUntil = activeUntil;
    }

    public ActiveVerificationValue(VerificationValue verificationValue, LocalDateTime activeUntil) {
        super(verificationValue.getId(), verificationValue.getBlockMetaData(), verificationValue.getHash());
        this.activeUntil = activeUntil;
    }
}
