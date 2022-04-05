package at.lucny.p2pbackup.user.domain;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Embeddable;

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
