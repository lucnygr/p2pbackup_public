package at.lucny.p2pbackup.network.service;

import at.lucny.p2pbackup.network.dto.ProtocolMessage;
import at.lucny.p2pbackup.network.service.handler.ExceptionLoggingHandler;
import at.lucny.p2pbackup.network.service.handler.MessageWrapperHandler;
import at.lucny.p2pbackup.network.service.handler.UserExtractingHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.function.Supplier;

public class ChannelPipelineInitializer extends ChannelInitializer<Channel> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelPipelineInitializer.class);

    private static final ProtobufVarint32LengthFieldPrepender FRAMER_DOWNSTREAM = new ProtobufVarint32LengthFieldPrepender();

    private static final ExceptionLoggingHandler LOGGING_HANDLER = new ExceptionLoggingHandler();

    private static final ProtobufEncoder PROTOBUF_ENCODER = new ProtobufEncoder();

    private static final ProtobufDecoder PROTOBUF_DECODER = new ProtobufDecoder(ProtocolMessage.getDefaultInstance());

    private static final UserExtractingHandler USER_EXTRACTING_HANDLER = new UserExtractingHandler();

    private static final MessageWrapperHandler MESSAGE_WRAPPER_HANDLER = new MessageWrapperHandler();

    private final LinkedHashMap<String, ChannelHandler> handlerMap;

    private final Supplier<SSLEngine> sslEngineSupplier;

    public ChannelPipelineInitializer(Supplier<SSLEngine> sslEngineSupplier) {
        this(sslEngineSupplier, new LinkedHashMap<>());
    }

    public ChannelPipelineInitializer(Supplier<SSLEngine> sslEngineSupplier, LinkedHashMap<String, ChannelHandler> additionalHandlers) {
        this.sslEngineSupplier = sslEngineSupplier;
        this.handlerMap = additionalHandlers;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        LOGGER.debug("create new pipeline");

        // create default pipeline from static method
        ChannelPipeline pipeline = ch.pipeline();

        SslHandler sslHandler = new SslHandler(this.sslEngineSupplier.get());
        pipeline.addLast("ssl", sslHandler);

        pipeline.addLast("framerDownstream", FRAMER_DOWNSTREAM);
        pipeline.addLast("framerUpstream", new ProtobufVarint32FrameDecoder());

        pipeline.addLast("protobufEncoder", PROTOBUF_ENCODER);
        pipeline.addLast("protobufDecoder", PROTOBUF_DECODER);

        pipeline.addLast(LOGGING_HANDLER);
        pipeline.addLast(USER_EXTRACTING_HANDLER);
        pipeline.addLast(MESSAGE_WRAPPER_HANDLER);

        for (Entry<String, ChannelHandler> entry : this.handlerMap.entrySet()) {
            pipeline.addLast(entry.getKey(), entry.getValue());
        }
    }
}
