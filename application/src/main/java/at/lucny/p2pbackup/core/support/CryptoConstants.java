package at.lucny.p2pbackup.core.support;

public final class CryptoConstants {

    private CryptoConstants() {
    }

    public static final String TLS_VERSION = "TLSv1.3";

    public static final String[] TLS_CIPHERS = new String[]{"TLS_AES_256_GCM_SHA384", "TLS_AES_128_GCM_SHA256", "TLS_AES_128_GCM_SHA256"};

    public static final String KEYPAIR_ALGORITHM = "RSA";

    public static final int KEYPAIR_KEYSIZE = 3072;

    public static final String KEY_DERIVATION_FUNCTION = "PBKDF2WithHmacSHA512";

    public static final String BLOCK_HASH_ALGORITHM = "SHA-256";

    public static final String FILE_HASH_ALGORITHM = "SHA-256";

    public static final String HMAC_BLOCK_ALGORITHM = "HmacSHA512";
}

