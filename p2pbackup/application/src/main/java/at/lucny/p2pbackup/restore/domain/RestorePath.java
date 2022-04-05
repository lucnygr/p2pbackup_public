package at.lucny.p2pbackup.restore.domain;

import at.lucny.p2pbackup.core.domain.AbstractEntity;
import at.lucny.p2pbackup.core.domain.PathVersion;
import lombok.*;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "RESTORE_PATH", uniqueConstraints = @UniqueConstraint(name = "uc_restore_path_path_version_id_path", columnNames = {"PATH_VERSION_ID","PATH"}))
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RestorePath extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PATH_VERSION_ID", nullable = false)
    @ToString.Exclude
    private PathVersion pathVersion;

    @Column(name = "PATH", nullable = false, length = 1024)
    private String path;

    @ManyToMany
    @JoinTable(name = "RESTORE_PATH_RESTORE_BLOCK_DATA",
            joinColumns =
            @JoinColumn(name = "RESTORE_PATH_ID", nullable = false),
            inverseJoinColumns =
            @JoinColumn(name = "RESTORE_BLOCK_DATA_ID", nullable = false)
    )
    @ToString.Exclude
    private Set<RestoreBlockData> missingBlocks = new HashSet<>();

    public RestorePath(PathVersion pathVersion, String path) {
        this.pathVersion = pathVersion;
        this.path = path;
    }
}
