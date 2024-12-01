package at.lucny.p2pbackup.network.service;

import at.lucny.p2pbackup.user.domain.NetworkAddress;
import at.lucny.p2pbackup.user.domain.User;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

public class NettyClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyClient.class);

    private final Bootstrap bootstrap;

    private final User user;

    private final Duration durationBetweenConnectionAttemptsInSeconds;

    private Channel clientChannel;

    private OffsetDateTime lastConnectAttempt;

    public NettyClient(Bootstrap bootstrap, User user) {
        this(bootstrap, user, Duration.ofMinutes(10));
    }

    public NettyClient(Bootstrap bootstrap, User user, Duration durationBetweenConnectionAttemptsInSeconds) {
        this(bootstrap, user, durationBetweenConnectionAttemptsInSeconds, null);
    }

    public NettyClient(Bootstrap bootstrap, User user, Duration durationBetweenConnectionAttemptsInSeconds, Channel clientChannel) {
        this.bootstrap = bootstrap;
        this.user = user;
        this.durationBetweenConnectionAttemptsInSeconds = durationBetweenConnectionAttemptsInSeconds;
        this.clientChannel = clientChannel;
    }

    public synchronized boolean connect() {
        if (!this.isConnected()) {
            if (this.lastConnectAttempt != null && this.lastConnectAttempt.isAfter(OffsetDateTime.now().minus(this.durationBetweenConnectionAttemptsInSeconds))) {
                return this.isConnected();
            }
            this.lastConnectAttempt = OffsetDateTime.now();

            for (NetworkAddress address : this.user.getAddresses()) {
                ChannelFuture future = this.bootstrap.connect(new InetSocketAddress(address.getHost(), address.getPort())).awaitUninterruptibly();
                if (future.isSuccess()) {
                    this.connectInternal(future.channel());
                    break;
                } else {
                    LOGGER.debug("could not connect to user={} with address={}:{}", this.user.getId(), address.getHost(), address.getPort(), future.cause());
                }
            }
        }
        return this.isConnected();
    }

    public synchronized boolean connect(Channel channel) {
        if (!this.isConnected()) {
            this.connectInternal(channel);
        } else {
            LOGGER.warn("client {} already connected. ignore given channel.", this.user.getId());
            return false;
        }
        return this.isConnected();
    }

    private void connectInternal(Channel channel) {
        if (!Objects.requireNonNull(channel).isOpen()) {
            LOGGER.warn("channel for user {} is not open. ignore given channel.", this.user.getId());
        }
        this.clientChannel = channel;
        this.clientChannel.closeFuture().addListener((ChannelFutureListener) channelFuture -> this.disconnect());
    }

    public void disconnect() {
        if (this.clientChannel != null && this.clientChannel.isOpen()) {
            this.clientChannel.close().awaitUninterruptibly();
        }
        this.clientChannel = null;
    }

    public boolean isDisconnected() {
        return !this.isConnected();
    }

    public boolean isConnected() {
        if (this.clientChannel == null) {
            return false;
        }
        return this.clientChannel.isOpen();
    }

    public ChannelFuture write(Object message) {
        if (this.isDisconnected() && !this.connect()) {
            throw new IllegalStateException("unable to connect to user=" + this.user.getId());
        }

        return this.clientChannel.writeAndFlush(message);
    }

    public User getUser() {
        return user;
    }
}
