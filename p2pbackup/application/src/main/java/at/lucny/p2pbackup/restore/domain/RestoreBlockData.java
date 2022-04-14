package at.lucny.p2pbackup.restore.domain;

import at.lucny.p2pbackup.core.domain.AbstractEntity;
import at.lucny.p2pbackup.core.domain.BlockMetaData;
import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "RESTORE_BLOCK_DATA")
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RestoreBlockData extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BLOCK_META_DATA_ID", nullable = false)
    @ToString.Exclude
    private BlockMetaData blockMetaData;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false)
    private RestoreType type;

}
