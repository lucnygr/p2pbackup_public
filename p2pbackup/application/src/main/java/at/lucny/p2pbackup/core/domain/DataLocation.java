package at.lucny.p2pbackup.core.domain;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "DATA_LOCATION", uniqueConstraints =
        {@UniqueConstraint(name = "uc_data_location_block_meta_data_id_user_id", columnNames = {"BLOCK_META_DATA_ID", "USER_ID"})})
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DataLocation extends AbstractEntityAudited {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "BLOCK_META_DATA_ID", nullable = false)
    @ToString.Exclude
    private BlockMetaData blockMetaData;

    @Column(name = "USER_ID", nullable = false)
    private String userId;

    @Column(name = "VERIFIED", nullable = false)
    protected LocalDateTime verified;
}
