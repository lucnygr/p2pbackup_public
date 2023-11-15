package at.lucny.p2pbackup.core.domain;

import lombok.*;

import jakarta.persistence.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "PATH_DATA")
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PathData extends AbstractEntityAudited {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ROOT_DIRECTORY_ID", nullable = false)
    @ToString.Exclude
    private RootDirectory rootDirectory;

    @Column(name = "PATH", nullable = false, length = 1024)
    private String path;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "PATH_DATA_ID", nullable = false)
    @ToString.Exclude
    private Set<PathVersion> versions = new HashSet<>();

    public PathData(RootDirectory rootDirectory, String path) {
        this(rootDirectory, Paths.get(path));
    }

    public PathData(RootDirectory rootDirectory, Path path) {
        this.rootDirectory = rootDirectory;
        this.path = path.toString();
    }

    public PathData(RootDirectory rootDirectory, Path path, Set<PathVersion> versions) {
        this(rootDirectory, path.toString(), versions);
    }
}
