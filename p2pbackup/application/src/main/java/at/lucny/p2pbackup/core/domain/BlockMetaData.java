package at.lucny.p2pbackup.core.domain;

import lombok.*;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "BLOCK_META_DATA")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class BlockMetaData extends AbstractEntityAudited {

    @Column(name = "HASH", length = 64, unique = true)
    private String hash;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "blockMetaData")
    @ToString.Exclude
    private Set<DataLocation> locations = new HashSet<>();

    public BlockMetaData(String id, String hash) {
        super(id);
        this.hash = hash;
    }

    public BlockMetaData(String hash) {
        this.hash = hash;
    }

    public void addDataLocation(DataLocation location) {
        this.locations.add(location);
        location.setBlockMetaData(this);
    }
}
