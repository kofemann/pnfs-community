package org.dcache.nfs;

import eu.emi.security.authn.x509.X509CertChainValidatorExt;
import eu.emi.security.authn.x509.helpers.ssl.SSLTrustManager;
import eu.emi.security.authn.x509.impl.CertificateUtils;
import eu.emi.security.authn.x509.impl.DirectoryCertChainValidator;
import eu.emi.security.authn.x509.impl.PEMCredential;
import java.util.Collections;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import org.springframework.beans.factory.FactoryBean;

/**
 *
 */
public class SslContextFactoryBean implements FactoryBean<SSLContext> {

    private String certFilePath;
    private String keyFilePath;
    private char[] keyFilePass;
    private String trustedCaBundle;

    public void setCertFilePath(String certFilePath) {
        this.certFilePath = certFilePath;
    }

    public void setKeyFilePath(String keyFilePath) {
        this.keyFilePath = keyFilePath;
    }

    public void setKeyFilePass(char[] keyFilePass) {
        this.keyFilePass = keyFilePass;
    }

    public void setTrustedCaBundle(String trustedCaBundle) {
        this.trustedCaBundle = trustedCaBundle;
    }

    @Override
    public SSLContext getObject() throws Exception {
        X509CertChainValidatorExt certificateValidator =
                 new DirectoryCertChainValidator(Collections.singletonList(trustedCaBundle), CertificateUtils.Encoding.PEM, -1, 5000, null);

        PEMCredential serviceCredentials = new PEMCredential(keyFilePath, certFilePath, keyFilePass);

        KeyManager keyManager = serviceCredentials.getKeyManager();
        KeyManager[] kms = new KeyManager[]{keyManager};
        SSLTrustManager tm = new SSLTrustManager(certificateValidator);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kms, new TrustManager[]{tm}, null);
        return ctx;
    }

    @Override
    public Class<?> getObjectType() {
        return SSLContext.class;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

}
