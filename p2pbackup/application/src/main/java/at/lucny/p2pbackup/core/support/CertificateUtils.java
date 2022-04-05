package at.lucny.p2pbackup.core.support;

import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.cert.*;

public class CertificateUtils {

    public void writeCertificate(X509Certificate certificate, Path fileName) throws IOException {
        try (JcaPEMWriter jcaPEMWriter = new JcaPEMWriter(Files.newBufferedWriter(fileName, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW))) {
            jcaPEMWriter.writeObject(certificate);
        }
    }

    public X509Certificate readCertificate(byte[] certificateBytes) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate cert = cf.generateCertificate(new ByteArrayInputStream(certificateBytes));
            return (X509Certificate) cert;
        } catch (CertificateException e) {
            throw new IllegalStateException("could not read certificate", e);
        }
    }

    public String getCommonName(X509Certificate certificate) {
        try {
            RDN[] rdns = new JcaX509CertificateHolder(certificate).getSubject().getRDNs(BCStyle.CN);
            if (rdns.length != 1) {
                throw new IllegalArgumentException("did not find issuer name in certificate");
            }
            return IETFUtils.valueToString(rdns[0].getFirst().getValue());
        } catch (CertificateEncodingException cee) {
            throw new IllegalStateException("could not read certificate", cee);
        }
    }
}
