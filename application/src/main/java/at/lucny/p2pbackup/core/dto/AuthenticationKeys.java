package at.lucny.p2pbackup.core.dto;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

public record AuthenticationKeys(KeyPair keyPair, X509Certificate certificate) {
}
