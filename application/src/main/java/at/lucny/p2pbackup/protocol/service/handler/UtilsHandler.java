package at.lucny.p2pbackup.protocol.service.handler;

import at.lucny.p2pbackup.network.dto.ProtocolMessage;
import at.lucny.p2pbackup.network.dto.ProtocolMessageWrapper;
import at.lucny.p2pbackup.network.service.handler.ClientHandler;
import at.lucny.p2pbackup.network.service.handler.ServerHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ChannelHandler.Sharable
public class UtilsHandler extends MessageToMessageDecoder<ProtocolMessageWrapper> implements ServerHandler, ClientHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(UtilsHandler.class);

    @Override
    protected void decode(ChannelHandlerContext ctx, ProtocolMessageWrapper protocolMessageWrapper, List<Object> out) {
        if (protocolMessageWrapper.protocolMessage().getMessageCase() == ProtocolMessage.MessageCase.TEXT) {
            LOGGER.info("{} sent: {}", protocolMessageWrapper.userId(), protocolMessageWrapper.protocolMessage().getText());
        } else {
            out.add(protocolMessageWrapper);
        }
    }

}
