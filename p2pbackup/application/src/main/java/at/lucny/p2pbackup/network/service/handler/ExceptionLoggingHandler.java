package at.lucny.p2pbackup.network.service.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.logging.LoggingHandler;

/**
 * Logs exceptions as warnings.
 */
@ChannelHandler.Sharable
public class ExceptionLoggingHandler extends LoggingHandler {

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        String userId = ctx.channel().attr(HandlerAttributes.ATT_USER_ID).get();
        logger.warn("caught exception for inbound message from user {}", userId, cause);
        ctx.close();
    }
}
