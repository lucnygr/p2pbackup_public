package at.lucny.p2pbackup.network.service;

import at.lucny.p2pbackup.user.domain.User;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public interface ClientService {

    @NotNull List<NettyClient> getClients();

    @NotNull NettyClient getClient(@NotNull User user);

    @NotNull NettyClient getClient(@NotNull String userId);

    @NotNull List<NettyClient> getOnlineClients();

    @NotNull List<NettyClient> getOnlineClients(@NotEmpty List<String> userIds);

    boolean isOnline(@NotNull String userId);

    boolean isOnline(@NotNull NettyClient client);

    void shutdownClients();
}
