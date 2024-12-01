package at.lucny.p2pbackup.network.service;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.application.support.StopApplicationEvent;
import at.lucny.p2pbackup.core.service.CryptoService;
import at.lucny.p2pbackup.core.support.NetworkConstants;
import at.lucny.p2pbackup.network.service.handler.ClientHandler;
import at.lucny.p2pbackup.user.domain.User;
import at.lucny.p2pbackup.user.repository.UserRepository;
import at.lucny.p2pbackup.user.support.UserAddedEvent;
import at.lucny.p2pbackup.user.support.UserChangedEvent;
import at.lucny.p2pbackup.user.support.UserDeletedEvent;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;

@Service
@Validated
public class ClientServiceNetty extends AbstractNettyService implements ClientService {

    private final P2PBackupProperties p2PBackupProperties;

    private final CryptoService cryptoService;

    private final UserRepository userRepository;

    private final EventLoopGroup eventLoopGroup;

    private final Bootstrap clientBootstrap;

    private final Map<String, NettyClient> usedClients = new HashMap<>();

    private final List<ClientHandler> additionalClientHandlers;

    public ClientServiceNetty(P2PBackupProperties p2PBackupProperties, CryptoService cryptoService, UserRepository userRepository, List<ClientHandler> additionalClientHandlers) {
        this.p2PBackupProperties = p2PBackupProperties;
        this.cryptoService = cryptoService;
        this.userRepository = userRepository;
        this.additionalClientHandlers = additionalClientHandlers;
        this.eventLoopGroup = new NioEventLoopGroup();
        this.clientBootstrap = this.createBootstrap();
    }

    @PostConstruct
    public void initializeClients() {
        List<User> users = this.userRepository.findAll();
        users = this.userRepository.fetchAddresses(users.stream().map(User::getId).toList());
        for (User user : users) {
            var client = new NettyClient(this.clientBootstrap, user, this.p2PBackupProperties.getNetwork().getDurationBetweenConnectionAttempts());
            this.usedClients.put(user.getId(), client);
        }
    }

    @TransactionalEventListener
    public synchronized void afterUserAdded(UserAddedEvent event) {
        LOGGER.info("adding client for user {}", event.getUserId());
        User user = this.userRepository.findByIdFetchAdresses(event.getUserId()).orElseThrow(() -> new IllegalArgumentException("Unknown user " + event.getUserId()));
        var client = new NettyClient(this.clientBootstrap, user, this.p2PBackupProperties.getNetwork().getDurationBetweenConnectionAttempts());
        this.usedClients.put(event.getUserId(), client);
    }

    @TransactionalEventListener
    public synchronized void afterUserDeleted(UserDeletedEvent event) {
        LOGGER.info("removing client for user {}", event.getUserId());
        NettyClient client = this.getClient(event.getUserId());
        this.usedClients.remove(event.getUserId());
        client.disconnect();
    }

    @TransactionalEventListener
    public synchronized void afterUserChanged(UserChangedEvent event) {
        LOGGER.info("reconnecting client for changed user {}", event.getUserId());
        NettyClient client = this.getClient(event.getUserId());
        this.usedClients.remove(event.getUserId());
        client.disconnect();
        User user = this.userRepository.findByIdFetchAdresses(event.getUserId()).orElseThrow(() -> new IllegalArgumentException("Unknown user " + event.getUserId()));
        client = new NettyClient(this.clientBootstrap, user, this.p2PBackupProperties.getNetwork().getDurationBetweenConnectionAttempts());
        this.usedClients.put(event.getUserId(), client);
    }

    private Bootstrap createBootstrap() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(this.eventLoopGroup).channel(NioSocketChannel.class).option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, NetworkConstants.CONNECT_TIMEOUT);

        LinkedHashMap<String, ChannelHandler> handlers = new LinkedHashMap<>();
        this.additionalClientHandlers.forEach(h -> handlers.put(h.getClass().getName(), h));

        bootstrap.handler(new ChannelPipelineInitializer(() -> this.cryptoService.createSslEngine(true), handlers));
        return bootstrap;
    }

    @Override
    public List<NettyClient> getClients() {
        return new ArrayList<>(this.usedClients.values());
    }

    @Override
    public NettyClient getClient(User user) {
        return this.getClient(user.getId());
    }

    @Override
    public NettyClient getClient(String userId) {
        if (this.usedClients.containsKey(userId)) {
            return this.usedClients.get(userId);
        }
        throw new IllegalStateException("unknown client " + userId);
    }

    @Override
    public List<NettyClient> getOnlineClients() {
        return this.usedClients.values().stream().filter(this::isOnline).toList();
    }

    @Override
    public List<NettyClient> getOnlineClients(List<String> userIds) {
        return userIds.stream().map(this::getClient).filter(this::isOnline).toList();
    }

    @Override
    public boolean isOnline(NettyClient client) {
        if (client.isDisconnected()) {
            if (client.connect()) {
                LOGGER.trace("connected to {}", client.getUser().getId());
            } else {
                LOGGER.trace("unable to connect to {}", client.getUser().getId());
            }
        }
        return client.isConnected();
    }

    @Override
    public boolean isOnline(String userId) {
        NettyClient client = this.getClient(userId);
        return this.isOnline(client);
    }

    private synchronized void closeClients() {
        LOGGER.trace("begin closeClients");

        for (NettyClient client : this.usedClients.values()) {
            client.disconnect();
        }

        LOGGER.trace("end closeClients");
    }

    @EventListener
    public void onApplicationEvent(StopApplicationEvent event) {
        this.shutdownClients();
    }

    @PreDestroy
    @Override
    public synchronized void shutdownClients() {
        LOGGER.trace("begin stop");

        this.closeClients();
        this.shutdownGroup(this.eventLoopGroup);

        LOGGER.trace("end stop");
    }
}
