package at.lucny.p2pbackup.core.domain;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.nio.file.Path;
import java.nio.file.Paths;

@Entity
@Table(name = "ROOT_DIRECTORY")
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RootDirectory extends AbstractEntityAudited {

    @Column(name = "NAME", nullable = false, length = 256, unique = true)
    private String name;

    @Column(name = "PATH", nullable = false, length = 1024)
    private String path;

    public RootDirectory(String id, String name, Path path) {
        super(id);
        this.name = name;
        this.path = path.toAbsolutePath().toString();
    }

    public RootDirectory(String name, String path) {
        this(name, Paths.get(path));
    }

    public RootDirectory(String name, Path path) {
        this.name = name;
        this.path = path.toAbsolutePath().toString();
    }

}
