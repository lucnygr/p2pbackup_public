package at.lucny.p2pbackup.core.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.Objects;
import java.util.UUID;

@MappedSuperclass
@Getter
@Setter
@ToString
public abstract class AbstractEntity {

    @Id
    @Column(name = "ID", length = 256, nullable = false)
    private String id;

    protected AbstractEntity() {
        this.id = UUID.randomUUID().toString().toUpperCase();
    }

    protected AbstractEntity(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractEntity that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
