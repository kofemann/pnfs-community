package org.dcache.nfs;

import java.nio.file.Paths;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;
import nl.altindag.ssl.pem.util.PemUtils;
import nl.altindag.ssl.util.TrustManagerUtils;
import org.conscrypt.OpenSSLProvider;
import org.springframework.beans.factory.FactoryBean;

/** */
public class SslContextFactoryBean implements FactoryBean<SSLContext> {


  private static final OpenSSLProvider OPEN_SSL_PROVIDER = new OpenSSLProvider();

  private String certificateFile;
  private String certificateKeyFile;
  private char[] keyFilePass;
  private String trustStore;

  private boolean insecure;

  public void setCertFilePath(String certFilePath) {
    this.certificateFile = certFilePath;
  }

  public void setKeyFilePath(String keyFilePath) {
    this.certificateKeyFile = keyFilePath;
  }

  public void setKeyFilePass(char[] keyFilePass) {
    this.keyFilePass = keyFilePass;
  }

  public void setTrustedCaBundle(String trustedCaBundle) {
    this.trustStore = trustedCaBundle;
  }

  public void setInsecure(boolean insecure) {
    this.insecure = insecure;
  }

  @Override
  public SSLContext getObject() throws Exception {

    X509ExtendedKeyManager keyManager =
        PemUtils.loadIdentityMaterial(Paths.get(certificateFile), Paths.get(certificateKeyFile));
    X509ExtendedTrustManager trustManager = insecure ?
            TrustManagerUtils.createDummyTrustManager() :
            PemUtils.loadTrustMaterial(Paths.get(trustStore));

    SSLContext sslContext = SSLContext.getInstance("TLSv1.3", OPEN_SSL_PROVIDER);
    sslContext.init(new KeyManager[]{keyManager}, new TrustManager[]{trustManager}, null);

    return sslContext;
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
