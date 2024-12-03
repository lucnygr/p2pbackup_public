package at.lucny.p2pbackup.user.domain;

import lombok.*;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * A network-address where a user is reachable via the ip-protocol.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class NetworkAddress {

    @Column(name = "HOST", nullable = false)
    private String host;

    @Column(name = "PORT", nullable = false)
    private int port;

}
