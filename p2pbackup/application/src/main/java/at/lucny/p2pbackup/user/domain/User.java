package at.lucny.p2pbackup.user.domain;

import lombok.*;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * A user (=Teilnehmer) that is an accepted partner in the p2p-backup-solution.
 */
@Entity
@Table(name = "USER")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class User {

    @Id
    @Column(name = "ID", nullable = false)
    @EqualsAndHashCode.Include
    private String id;

    @Lob
    @Column(name = "CERTIFICATE", nullable = false)
    private byte[] certificate;

    @Column(name = "ALLOW_BACKUP_DATA_FROM_USER", nullable = false)
    private boolean allowBackupDataFromUser;

    @Column(name = "ALLOW_BACKUP_DATA_TO_USER", nullable = false)
    private boolean allowBackupDataToUser;

    @ElementCollection
    @CollectionTable(name = "USER_NETWORK_ADDRESS", joinColumns = @JoinColumn(name = "USER_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_USER_NETWORK_ADDRESS_USER_ID")))
    private Set<NetworkAddress> addresses = new HashSet<>();

    public User(String id, byte[] certificate, boolean allowBackupDataFromUser, boolean allowBackupDataToUser, NetworkAddress address) {
        this.id = id;
        this.certificate = certificate;
        this.allowBackupDataFromUser = allowBackupDataFromUser;
        this.allowBackupDataToUser = allowBackupDataToUser;
        this.addresses.add(address);
    }
}
