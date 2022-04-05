package at.lucny.p2pbackup.restore.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RecoverRootDirectory {

    @Column(name = "ROOT_DIRECTORY_ID", nullable = false, length = 256)
    private String id;

    @Column(name = "ROOT_DIRECTORY_NAME", nullable = false, length = 256)
    private String name;

}
