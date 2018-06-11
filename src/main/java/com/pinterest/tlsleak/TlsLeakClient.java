package com.pinterest.tlsleak;

import com.twitter.finagle.Address;
import com.twitter.finagle.Http;
import com.twitter.finagle.Service;
import com.twitter.finagle.builder.ClientBuilder;
import com.twitter.finagle.http.Methods;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import com.twitter.finagle.ssl.ApplicationProtocolsConfig;
import com.twitter.finagle.ssl.CipherSuitesConfig;
import com.twitter.finagle.ssl.Engine;
import com.twitter.finagle.ssl.KeyCredentialsConfig;
import com.twitter.finagle.ssl.ProtocolsConfig;
import com.twitter.finagle.ssl.TrustCredentialsConfig;
import com.twitter.finagle.ssl.client.SslClientConfiguration;
import com.twitter.finagle.ssl.client.SslClientEngineFactory;
import com.twitter.finagle.transport.Transport;
import com.twitter.util.Await;
import com.twitter.util.Future;
import com.twitter.util.Futures;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import scala.Option;
import scala.runtime.AbstractFunction0;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

public class TlsLeakClient {

  private final static int PORT = 9192;

  public static void main(String[] args) {

    if (args.length != 3) {
      System.err.println("Usage: <prog> <keystore> <truststore> <password>");
      System.exit(2);
    }

    String keystore = args[0];
    String truststore = args[1];
    char[] password = args[2].toCharArray();

    try {
      final ClientBuilder builder =
          ClientBuilder
              .get()
              .hosts("localhost:" + PORT)
              .hostConnectionLimit(1)
              .stack(Http
                  .client()
                  .configured(new Transport.ClientSsl(Option.<SslClientConfiguration>apply(
                      new SslClientConfiguration(
                          Option.<String>empty(),
                          KeyCredentialsConfig.UNSPECIFIED,
                          TrustCredentialsConfig.UNSPECIFIED,
                          CipherSuitesConfig.UNSPECIFIED,
                          ProtocolsConfig.UNSPECIFIED,
                          ApplicationProtocolsConfig.UNSPECIFIED))).mk())
                  .configured(new SslClientEngineFactory.Param(
                      new tlsClientEngineFactory(keystore, truststore, password)).mk()));

      final AtomicInteger counter =  new AtomicInteger(1000000);

      List<Future<BoxedUnit>> runners = new ArrayList<>();
      for (int i=0; i <= 16; i++) {
        Future<BoxedUnit> runner = Future.whileDo(
            new AbstractFunction0<Object>() {
              @Override
              public Object apply() {
                return counter.decrementAndGet() > 0;
              }
            },
            new AbstractFunction0<Future<BoxedUnit>>() {
              @Override
              public Future<BoxedUnit> apply() {
                final Service<Request, Response> client = ClientBuilder.safeBuild(builder);
                return client
                    .apply(Request.apply(Methods.GET, "/"))
                    .flatMap(new AbstractFunction1<Response, Future<BoxedUnit>>() {
                      @Override
                      public Future<BoxedUnit> apply(Response response) {
                        return client.close();
                      }
                    });
              }
            });
        runners.add(runner);
      }
      Await.result(Futures.join(runners));
    } catch (Exception e) {
      System.out.println("Exception!");
      System.out.println(e.getClass().getName());
      System.out.println(e.getMessage());
      System.exit(1);
    }
  }

  public static class tlsClientEngineFactory extends SslClientEngineFactory {

    SslContext sslContext;

    public tlsClientEngineFactory(String keystore, String truststore, char[] password)
        throws Exception {
      sslContext = SslContextBuilder
          .forClient()
          .keyManager(newKeyManagerFactory(keystore, password))
          .trustManager(newTrustManagerFactory(truststore, password))
          .sslProvider(SslProvider.OPENSSL)
          .build();
    }

    @Override
    public Engine apply(Address address, SslClientConfiguration sslClientConfiguration) {
      SSLEngine engine = sslContext.newEngine(UnpooledByteBufAllocator.DEFAULT);
      return new Engine(engine, false, "generic");
    }
  }

  private static KeyManagerFactory newKeyManagerFactory(String keystorePath, char[] password)
      throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException,
             UnrecoverableKeyException {
    KeyStore keyStore = KeyStore.getInstance("JKS");
    keyStore.load(new FileInputStream(keystorePath), password);
    KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
        KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, password);
    return keyManagerFactory;
  }

  private static TrustManagerFactory newTrustManagerFactory(String truststorePath, char[] password)
      throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
    KeyStore keyStore = KeyStore.getInstance("JKS");
    keyStore.load(new FileInputStream(truststorePath), password);
    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(keyStore);
    return trustManagerFactory;
  }
}
