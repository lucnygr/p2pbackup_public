package at.lucny.p2pbackup.core.service;

import at.lucny.p2pbackup.core.support.SecretKeyGenerator;

import javax.net.ssl.SSLEngine;
import jakarta.validation.constraints.NotNull;

public interface CryptoService {
    @NotNull SecretKeyGenerator getSecretKeyGenerator();

    @NotNull SSLEngine createSslEngine(boolean useAsClient);

}
