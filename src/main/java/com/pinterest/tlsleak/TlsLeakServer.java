package com.pinterest.tlsleak;

import com.twitter.finagle.Http;
import com.twitter.finagle.Service;
import com.twitter.finagle.builder.ClientBuilder;
import com.twitter.finagle.builder.ServerBuilder;
import com.twitter.finagle.client.StackBasedClient;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import com.twitter.finagle.http.Status;
import com.twitter.finagle.server.StackBasedServer;
import com.twitter.finagle.ssl.ApplicationProtocolsConfig;
import com.twitter.finagle.ssl.CipherSuitesConfig;
import com.twitter.finagle.ssl.ClientAuthConfig;
import com.twitter.finagle.ssl.Engine;
import com.twitter.finagle.ssl.KeyCredentialsConfig;
import com.twitter.finagle.ssl.ProtocolsConfig;
import com.twitter.finagle.ssl.TrustCredentialsConfig;
import com.twitter.finagle.ssl.server.SslServerConfiguration;
import com.twitter.finagle.ssl.server.SslServerEngineFactory;
import com.twitter.finagle.transport.Transport;
import com.twitter.util.Future;
import com.twitter.util.Futures;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import org.jboss.netty.buffer.ChannelBuffers;
import scala.Function0;
import scala.Function1;
import scala.Option;
import scala.Unit;
import scala.collection.JavaConversions;
import scala.collection.JavaConverters;
import scala.runtime.AbstractFunction0;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

public class TlsLeakServer {

  private final static int PORT = 9192;
  private final static String[] ENABLED_PROTOCOLS = {"TLSv1.2"};

  public static void main(String[] args) {

    if (args.length != 3) {
      System.err.println("Usage: <prog> <keystore> <truststore> <password>");
      System.exit(2);
    }

    String keystore = args[0];
    String truststore = args[1];
    char[] password = args[2].toCharArray();

    try {
      Service<Request, Response> service = new Service<Request, Response>() {
        @Override
        public Future<Response> apply(Request request) {
          Response response = Response.apply(Status.Ok());
          response.setContentString("debuggingtheleak");
          Future<Response> future = Future.value(response);
          return future;
        }
      };

      final SslContext sslServerContext = SslContextBuilder
          .forServer(newKeyManagerFactory(keystore, password))
          .trustManager(newTrustManagerFactory(truststore, password))
          .clientAuth(ClientAuth.REQUIRE)
          .sslProvider(SslProvider.OPENSSL)
          .build();

      ServerBuilder.safeBuild(service,
          ServerBuilder
              .get()
              .bindTo(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT))
              .name("TlsLeakServer")
              .stack(Http
                  .server()
                  .configured(new Transport.ServerSsl(Option.<SslServerConfiguration>apply(
                      new SslServerConfiguration(
                          KeyCredentialsConfig.UNSPECIFIED,
                          ClientAuthConfig.NEEDED, TrustCredentialsConfig.UNSPECIFIED,
                          CipherSuitesConfig.UNSPECIFIED,
                          ProtocolsConfig.enabled(Arrays.asList(ENABLED_PROTOCOLS)),
                          ApplicationProtocolsConfig.UNSPECIFIED)
                  )).mk())
                  .configured(new SslServerEngineFactory.Param(
                      new tlsServerEngineFactory(keystore, truststore, password)).mk())));

    } catch (Exception e) {
      System.out.println("Exception!");
      System.out.println(e.getClass().getName());
      System.out.println(e.getMessage());
      System.exit(1);
    }
  }

  public static class tlsServerEngineFactory extends SslServerEngineFactory {

    SslContext sslContext;

    public tlsServerEngineFactory(String keystore, String truststore, char[] password)
        throws Exception {
      sslContext = SslContextBuilder
          .forServer(newKeyManagerFactory(keystore, password))
          .trustManager(newTrustManagerFactory(truststore, password))
          .sslProvider(SslProvider.OPENSSL)
          .clientAuth(ClientAuth.REQUIRE)
          .build();
    }

    @Override
    public Engine apply(SslServerConfiguration sslServerConfiguration) {
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
