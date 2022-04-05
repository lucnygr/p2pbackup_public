package at.lucny.p2pbackup.network.service.handler;

import io.netty.util.AttributeKey;

public final class HandlerAttributes {

    private HandlerAttributes() {
    }

    public static final AttributeKey<String> ATT_USER_ID = AttributeKey.newInstance("userId");
}
