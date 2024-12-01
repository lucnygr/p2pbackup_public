package at.lucny.p2pbackup.verification.domain;

import at.lucny.p2pbackup.core.domain.AbstractEntity;
import at.lucny.p2pbackup.core.domain.BlockMetaData;
import lombok.*;

import jakarta.persistence.*;

@MappedSuperclass
@Getter
@Setter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AbstractVerificationValue extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BLOCK_META_DATA_ID", nullable = false)
    @ToString.Exclude
    private BlockMetaData blockMetaData;

    @Column(name = "HASH", nullable = false)
    private String hash;

    protected AbstractVerificationValue(String id, BlockMetaData blockMetaData, String hash) {
        super(id);
        this.blockMetaData = blockMetaData;
        this.hash = hash;
    }


}
