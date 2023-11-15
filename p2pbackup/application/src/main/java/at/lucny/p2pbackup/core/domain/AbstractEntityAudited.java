package at.lucny.p2pbackup.core.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@MappedSuperclass
@Getter
@Setter
@ToString(callSuper = true)
public abstract class AbstractEntityAudited extends AbstractEntity {

    @Column(name = "DATE_CREATED", nullable = false)
    protected LocalDateTime dateCreated;

    @Column(name = "DATE_UPDATED", nullable = false)
    protected LocalDateTime dateUpdated;

    protected AbstractEntityAudited() {
        super();
    }

    protected AbstractEntityAudited(String id) {
        super(id);
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        this.dateCreated = now;
        this.dateUpdated = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.dateUpdated = LocalDateTime.now(ZoneOffset.UTC);
    }

}
