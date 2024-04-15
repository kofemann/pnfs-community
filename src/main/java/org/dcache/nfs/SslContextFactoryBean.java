package org.dcache.nfs;

import java.nio.file.Paths;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;
import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.pem.util.PemUtils;
import org.springframework.beans.factory.FactoryBean;

/** */
public class SslContextFactoryBean implements FactoryBean<SSLContext> {

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
    X509ExtendedTrustManager trustManager = PemUtils.loadTrustMaterial(Paths.get(trustStore));

    var sslFactoryBuilder = SSLFactory.builder().withIdentityMaterial(keyManager);

    if (insecure) {
      sslFactoryBuilder = sslFactoryBuilder.withDummyTrustMaterial();
    } else {
      sslFactoryBuilder = sslFactoryBuilder.withTrustMaterial(trustManager);
    }

    return sslFactoryBuilder.build().getSslContext();
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
