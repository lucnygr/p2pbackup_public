package at.lucny.p2pbackup.network.service;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.application.support.StopApplicationEvent;
import at.lucny.p2pbackup.core.service.CryptoService;
import at.lucny.p2pbackup.network.service.handler.ClientChannelReusingHandler;
import at.lucny.p2pbackup.network.service.handler.ServerHandler;
import at.lucny.p2pbackup.user.support.UserAddedEvent;
import at.lucny.p2pbackup.user.support.UserChangedEvent;
import at.lucny.p2pbackup.user.support.UserDeletedEvent;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@Validated
public class ServerServiceNetty extends AbstractNettyService implements ServerService {

    private final CryptoService cryptoService;

    private final EventLoopGroup bossGroup;

    private final EventLoopGroup workerGroup;

    private final P2PBackupProperties p2PBackupProperties;

    private final ClientService clientService;

    private Channel socketChannel;

    private final List<ServerHandler> additionalServerHandlers;

    public ServerServiceNetty(CryptoService cryptoService, P2PBackupProperties p2PBackupProperties, ClientService clientService, List<ServerHandler> additionalServerHandlers) {
        this.cryptoService = cryptoService;
        this.p2PBackupProperties = p2PBackupProperties;
        this.clientService = clientService;
        this.additionalServerHandlers = additionalServerHandlers;
        this.bossGroup = new NioEventLoopGroup();
        this.workerGroup = new NioEventLoopGroup();
    }

    @PostConstruct
    @Override
    public synchronized void startServer() {
        LOGGER.trace("begin startServer");

        ServerBootstrap serverBootstrap = this.createBootstrap();
        try {
            LOGGER.info("start server on port {}", this.p2PBackupProperties.getNetwork().getPort());
            var socketChannelFuture = serverBootstrap.bind(this.p2PBackupProperties.getNetwork().getPort()).sync();
            this.socketChannel = socketChannelFuture.channel();
        } catch (ChannelException e) {
            this.shutdownServer();
            throw new IllegalStateException("Unable to start server", e);
        } catch (InterruptedException e) {
            this.shutdownServer();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unable to start server", e);
        }

        LOGGER.trace("end startServer");
    }

    @TransactionalEventListener
    public synchronized void afterUserAdded(UserAddedEvent event) {
        LOGGER.info("restarting server to support new user {}", event.getUserId());
        this.stopServer();
        this.startServer();
    }

    @TransactionalEventListener
    public synchronized void afterUserDeleted(UserDeletedEvent event) {
        LOGGER.info("restarting server to remove support for deleted user {}", event.getUserId());
        this.stopServer();
        this.startServer();
    }

    @TransactionalEventListener
    public synchronized void afterUserChanged(UserChangedEvent event) {
        LOGGER.info("restarting server to support changing of user {}", event.getUserId());
        this.stopServer();
        this.startServer();
    }

    private synchronized void stopServer() {
        LOGGER.trace("begin stopServer");

        if (this.socketChannel != null && this.socketChannel.isOpen()) {
            LOGGER.info("stopping server on port {}", this.p2PBackupProperties.getNetwork().getPort());
            this.socketChannel.close().syncUninterruptibly();
        }

        LOGGER.trace("end stopServer");
    }


    @EventListener
    public void onApplicationEvent(StopApplicationEvent event) {
        this.shutdownServer();
    }

    @PreDestroy
    @Override
    public synchronized void shutdownServer() {
        LOGGER.trace("begin shutdownServer");

        this.stopServer();
        this.shutdownGroup(this.bossGroup);
        this.shutdownGroup(this.workerGroup);

        LOGGER.trace("end shutdownServer");
    }

    private ServerBootstrap createBootstrap() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(this.bossGroup, this.workerGroup).channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 100).childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        LinkedHashMap<String, ChannelHandler> handlers = new LinkedHashMap<>();
        handlers.put("clientChannelReusingHandler", new ClientChannelReusingHandler(this.clientService));
        this.additionalServerHandlers.forEach(h -> handlers.put(h.getClass().getName(), h));

        bootstrap.childHandler(new ChannelPipelineInitializer(() -> this.cryptoService.createSslEngine(false), handlers));
        return bootstrap;
    }

}
