package at.lucny.p2pbackup.shell;

import at.lucny.p2pbackup.network.dto.ProtocolMessage;
import at.lucny.p2pbackup.network.service.ClientService;
import at.lucny.p2pbackup.network.service.NettyClient;
import at.lucny.p2pbackup.network.service.ServerService;
import at.lucny.p2pbackup.user.domain.User;
import at.lucny.p2pbackup.user.service.UserService;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.Optional;

@ShellComponent
public class NetworkCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkCommands.class);

    private final ServerService serverService;

    private final ClientService clientService;

    private final UserService userService;

    public NetworkCommands(ServerService serverService, ClientService clientService, UserService userService) {
        this.serverService = serverService;
        this.clientService = clientService;
        this.userService = userService;
    }

    @ShellMethod("start the internal server-socket")
    public void startServer() {
        this.serverService.startServer();
    }

    @ShellMethod("stop the internal server-socket")
    public void stopServer() {
        this.serverService.shutdownServer();
    }

    @ShellMethod("writes text to client with given id")
    public void write(String userId, String message) {
        Optional<User> user = this.userService.findUser(userId);
        if (user.isPresent()) {
            NettyClient client = this.clientService.getClient(user.get());
            ChannelFuture future = client.write(ProtocolMessage.newBuilder().setText(message).build());
            future.addListener((ChannelFutureListener) channelFuture -> {
                if (channelFuture.isSuccess()) {
                    LOGGER.info("send message {} to user {}", message, userId);
                } else {
                    LOGGER.warn("could not send message to user {}", userId);
                }
            });
        } else {
            LOGGER.info("user {} not known", userId);
        }
    }

}
