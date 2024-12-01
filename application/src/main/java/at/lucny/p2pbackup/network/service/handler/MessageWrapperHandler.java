package at.lucny.p2pbackup.network.service.handler;

import at.lucny.p2pbackup.network.dto.ProtocolMessage;
import at.lucny.p2pbackup.network.dto.ProtocolMessageWrapper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;
import java.util.Objects;

@ChannelHandler.Sharable
public class MessageWrapperHandler extends MessageToMessageDecoder<ProtocolMessage> {

    @Override
    protected void decode(ChannelHandlerContext ctx, ProtocolMessage message, List<Object> out) throws Exception {
        String userId = ctx.channel().attr(HandlerAttributes.ATT_USER_ID).get();
        Objects.requireNonNull(userId, "userId of connected peer is null");
        out.add(new ProtocolMessageWrapper(userId, message));
    }
}
