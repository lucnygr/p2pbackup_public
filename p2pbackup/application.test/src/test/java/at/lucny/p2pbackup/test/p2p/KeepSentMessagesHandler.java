package at.lucny.p2pbackup.test.p2p;

import at.lucny.p2pbackup.network.dto.ProtocolMessageWrapper;
import at.lucny.p2pbackup.network.service.handler.ClientHandler;
import at.lucny.p2pbackup.network.service.handler.ServerHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
@ChannelHandler.Sharable
public class KeepSentMessagesHandler extends MessageToMessageDecoder<ProtocolMessageWrapper> implements ServerHandler, ClientHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeepSentMessagesHandler.class);

    private final List<ProtocolMessageWrapper> messages = new ArrayList<>();

    @Override
    protected void decode(ChannelHandlerContext ctx, ProtocolMessageWrapper msg, List<Object> out) throws Exception {
        this.messages.add(msg);
        out.add(msg);
    }

    public List<ProtocolMessageWrapper> getMessages() {
        return new ArrayList<>(this.messages);
    }
}
