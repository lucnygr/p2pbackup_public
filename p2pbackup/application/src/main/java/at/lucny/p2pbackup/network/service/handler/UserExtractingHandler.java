package at.lucny.p2pbackup.network.service.handler;

import at.lucny.p2pbackup.core.support.CertificateUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

@ChannelHandler.Sharable
public class UserExtractingHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserExtractingHandler.class);

    private final CertificateUtils certificateUtils = new CertificateUtils();

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent sslEvent && sslEvent.isSuccess()) {
            String userId = this.getUserId(ctx);
            LOGGER.debug("new connection to user {}", userId);
            ctx.channel().attr(HandlerAttributes.ATT_USER_ID).set(userId);
        }
        ctx.fireUserEventTriggered(evt);
    }

    private String getUserId(ChannelHandlerContext ctx) throws SSLPeerUnverifiedException {
        SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
        SSLEngine sslEngine = sslHandler.engine();
        Certificate[] certificates = sslEngine.getSession().getPeerCertificates();
        X509Certificate rootCertificate = (X509Certificate) certificates[certificates.length - 1];
        String commonName = this.certificateUtils.getCommonName(rootCertificate);
        return commonName;
    }
}
