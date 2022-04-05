package at.lucny.p2pbackup.restore.domain;

import at.lucny.p2pbackup.core.domain.AbstractEntity;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "RECOVER_BACKUP_INDEX")
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RecoverBackupIndex extends AbstractEntity {

    @Column(name = "DATE", nullable = false)
    private LocalDateTime date;

    @ElementCollection
    @CollectionTable(name = "RECOVER_BACKUP_INDEX_ROOT_DIRECTORY", joinColumns = @JoinColumn(name = "RECOVER_BACKUP_INDEX_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_RECOVER_BACKUP_INDEX_ROOT_DIRECTORY_RECOVER_BACKUP_INDEX_ID")))
    private Set<RecoverRootDirectory> rootDirectories;

    @ElementCollection
    @CollectionTable(name = "RECOVER_BACKUP_INDEX_VERSION_BLOCK", joinColumns = @JoinColumn(name = "RECOVER_BACKUP_INDEX_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_RECOVER_BACKUP_INDEX_VERSION_BLOCK_RECOVER_BACKUP_INDEX_ID")))
    @Column(name = "BLOCK_ID", nullable = false)
    private Set<String> versionBlockIds;

}
