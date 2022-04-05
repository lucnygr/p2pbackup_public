package at.lucny.p2pbackup.network.service.handler;

import at.lucny.p2pbackup.network.service.ClientService;
import at.lucny.p2pbackup.network.service.NettyClient;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@ChannelHandler.Sharable
public class ClientChannelReusingHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientChannelReusingHandler.class);

    private final ClientService clientService;

    public ClientChannelReusingHandler(ClientService clientService) {
        this.clientService = clientService;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent sslEvent && sslEvent.isSuccess()) {
            String userId = ctx.channel().attr(HandlerAttributes.ATT_USER_ID).get();
            Objects.requireNonNull(userId, "userId of connected peer is null");
            LOGGER.debug("setting inbound channel for user {} also for client", userId);
            NettyClient client = this.clientService.getClient(userId);
            if (!client.connect(ctx.channel())) {
                LOGGER.warn("could not reuse new channel for user {}", userId);
            }
        }
        ctx.fireUserEventTriggered(evt);
    }

}
