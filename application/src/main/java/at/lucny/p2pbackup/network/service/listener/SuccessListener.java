package at.lucny.p2pbackup.network.service.listener;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

public class SuccessListener implements ChannelFutureListener {

    private final Runnable runnable;

    public SuccessListener(Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public void operationComplete(ChannelFuture channelFuture) throws Exception {
        if (channelFuture.isSuccess()) {
            this.runnable.run();
        }
    }
}
