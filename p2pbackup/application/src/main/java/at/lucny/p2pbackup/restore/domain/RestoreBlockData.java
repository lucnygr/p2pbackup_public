package at.lucny.p2pbackup.restore.domain;

import at.lucny.p2pbackup.core.domain.BlockMetaDataId;
import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "RESTORE_BLOCK_DATA")
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RestoreBlockData {

    @EmbeddedId
    private BlockMetaDataId blockMetaDataId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false)
    private RestoreType type;

}
