package at.lucny.p2pbackup.core.domain;

import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "CLOUD_UPLOAD")
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CloudUpload extends AbstractEntityAudited {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BLOCK_META_DATA_ID", nullable = false, unique = true)
    @ToString.Exclude
    private BlockMetaData blockMetaData;

    @Column(name = "MAC_SECRET", length = 64, nullable = false)
    private String macSecret;

    @Column(name = "ENCRYPTED_BLOCK_MAC", length = 128, nullable = false)
    private String encryptedBlockMac;

    @Column(name = "PROVIDER_ID", length = 128)
    private String providerId;

    @Column(name = "SHARE_URL", length = 512)
    private String shareUrl;

    public CloudUpload(BlockMetaData blockMetaData) {
        this.blockMetaData = blockMetaData;
    }

    public CloudUpload(BlockMetaData blockMetaData, String macSecret, String encryptedBlockMac) {
        this.blockMetaData = blockMetaData;
        this.macSecret = macSecret;
        this.encryptedBlockMac = encryptedBlockMac;
    }
}
