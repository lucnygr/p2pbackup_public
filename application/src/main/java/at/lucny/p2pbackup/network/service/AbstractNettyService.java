package at.lucny.p2pbackup.network.service;

import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractNettyService {

    protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    protected void shutdownGroup(EventLoopGroup eventLoopGroup) {
        if (eventLoopGroup != null && !eventLoopGroup.isShutdown()) {
            LOGGER.debug("shutting down event-loop-group");
            eventLoopGroup.shutdownGracefully();

            try {
                // Wait until all threads are terminated.
                eventLoopGroup.terminationFuture().sync();
                LOGGER.debug("event-loop-group terminated");
            } catch (InterruptedException e) {
                LOGGER.error("Shutdown of event-loop-group.", e);
                Thread.currentThread().interrupt();
            }
        } else {
            LOGGER.debug("event-loop-group already stopped");
        }
    }
}
