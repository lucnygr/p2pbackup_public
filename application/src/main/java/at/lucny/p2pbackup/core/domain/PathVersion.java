package at.lucny.p2pbackup.core.domain;

import lombok.*;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "PATH_VERSION")
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PathVersion extends AbstractEntity {

    @Column(name = "DATE", nullable = false)
    private LocalDateTime date;

    @Column(name = "HASH", length = 64)
    private String hash;

    @Column(name = "DELETED", nullable = false)
    private Boolean deleted = Boolean.FALSE;

    @ManyToMany
    @JoinTable(name = "PATH_VERSION_BLOCK_META_DATA",
            joinColumns =
            @JoinColumn(name = "PATH_VERSION_ID", nullable = false),
            inverseJoinColumns =
            @JoinColumn(name = "BLOCK_META_DATA_ID", nullable = false)
    )
    @OrderColumn(name = "POSITION", nullable = false)
    @ToString.Exclude
    private List<BlockMetaData> blocks = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BLOCK_META_DATA_ID", nullable = false)
    @ToString.Exclude
    private BlockMetaData versionBlock;

    public PathVersion(LocalDateTime date, Boolean deleted) {
        this.date = date;
        this.deleted = deleted;
    }

    public PathVersion(LocalDateTime date, String hash) {
        this.date = date;
        this.hash = hash;
    }
}
