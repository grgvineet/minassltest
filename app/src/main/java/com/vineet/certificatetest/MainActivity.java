package com.vineet.certificatetest;

import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.filter.ssl.BogusTrustManagerFactory;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;


public class MainActivity extends ActionBarActivity {

    private final int PORT = 54321;
    TextView textView;
    Button button,connect;
    EditText editText,remoteAddress;

    IoSession ioSession = null;

    NioSocketAcceptor acceptor;

    TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
//                    return null;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType) {
        }

    }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BouncyCastleProvider BC = new BouncyCastleProvider();

        textView = (TextView)findViewById(R.id.text);
        editText= (EditText)findViewById(R.id.editText);
        button = (Button)findViewById(R.id.button);
        remoteAddress = (EditText)findViewById(R.id.remoteAddress);
        connect = (Button)findViewById(R.id.connect);

        initializeRsaKeys();

        // Initialise socket acceptor
        try {
            acceptor = new NioSocketAcceptor();
            acceptor.setHandler(tcpHandlerAcceptor);
            acceptor.getSessionConfig().setKeepAlive(true);
            acceptor.getSessionConfig().setReuseAddress(true);
            TextLineCodecFactory textLineFactory = new TextLineCodecFactory(Charset.defaultCharset(), LineDelimiter.UNIX, LineDelimiter.UNIX);
            textLineFactory.setDecoderMaxLineLength(512*1024); //Allow to receive up to 512kb of data
            acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(textLineFactory));
            acceptor.getFilterChain().addFirst("sslFilter", getSslFilter(false));
            acceptor.bind(new InetSocketAddress(PORT));
        }catch (Exception e){
            e.printStackTrace();
        }

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ioSession != null){
                    ioSession.write(editText.getText().toString());
                }
            }
        });


    }

    private void initializeRsaKeys() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        if (!settings.contains("publicKey") || !settings.contains("privateKey")) {

            KeyPair keyPair;
            try {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                keyPair = keyGen.genKeyPair();
            } catch(Exception e) {
                e.printStackTrace();
                Log.e("KDE/initializeRsaKeys", "Exception");
                return;
            }

            byte[] publicKey = keyPair.getPublic().getEncoded();
            byte[] privateKey = keyPair.getPrivate().getEncoded();

            SharedPreferences.Editor edit = settings.edit();
            edit.putString("publicKey",Base64.encodeToString(publicKey, 0).trim()+"\n");
            edit.putString("privateKey",Base64.encodeToString(privateKey, 0));
            edit.apply();

            initializeCertificate(keyPair);

        }


    }

    public void initializeCertificate(KeyPair keyPair) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        if (!settings.contains("certificate")) {
            try {

                BouncyCastleProvider BC = new BouncyCastleProvider();

                X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE);
                nameBuilder.addRDN(BCStyle.CN, "vineet");
                nameBuilder.addRDN(BCStyle.OU, "kdeconnect");
                nameBuilder.addRDN(BCStyle.O, "kde");
                Date notBefore = new Date(System.currentTimeMillis());
                Date notAfter = new Date(System.currentTimeMillis() + System.currentTimeMillis());
                X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                        nameBuilder.build(),
                        BigInteger.ONE,
                        notBefore,
                        notAfter,
                        nameBuilder.build(),
                        keyPair.getPublic()
                );
                ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSAEncryption").setProvider(BC).build(keyPair.getPrivate());
                X509Certificate certificate = new JcaX509CertificateConverter().setProvider(BC).getCertificate(certificateBuilder.build(contentSigner));

                SharedPreferences.Editor edit = settings.edit();
                edit.putString("certificate",Base64.encodeToString(certificate.getEncoded(), 0));
                edit.apply();

            } catch(Exception e) {
                e.printStackTrace();
                Log.e("KDE/initializeRsaKeys","Exception");
                return;
            }


        }
    }

    private IoHandler tcpHandlerConnector = new IoHandlerAdapter() {
        @Override
        public void sessionCreated(IoSession session) throws Exception {
            Log.e("Session Connector","Session created " +  session.getId());

        }

        @Override
        public void sessionOpened(IoSession session) throws Exception {
            Log.e("Session Connector", "Session opened " + session.getId());
//            ((SslFilter)session.getFilterChain().get("sslFilter")).startSsl(session);
        }

        @Override
        public void sessionClosed(IoSession session) throws Exception {
            Log.e("Session Connector", "Session closed " +  session.getId());
        }

        @Override
        public void sessionIdle(IoSession session, IdleStatus status) throws Exception {

        }

        @Override
        public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
            cause.printStackTrace();
            session.close(true);
        }

        @Override
        public void messageReceived(IoSession session, Object message) throws Exception {
            final String receivedMessage = (String) message;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textView.setText(receivedMessage);
                }
            });


        }

        @Override
        public void messageSent(IoSession session, Object message) throws Exception {
            Log.e("Session Connector", "Message send " +  session.getId() + " " + (String)message );
        }

    };

    private IoHandler tcpHandlerAcceptor = new IoHandlerAdapter() {
        @Override
        public void sessionCreated(IoSession session) throws Exception {
            Log.e("Session Acceptor","Session created " +  session.getId());
            ioSession = session;
            if (session.isConnected()){
                Log.e("Session Acceptor", "Session is connected");
            }

        }

        @Override
        public void sessionOpened(IoSession session) throws Exception {
            Log.e("Session Acceptor", "Session opened " + session.getId());

            session.write("blah!!!");
//            SslFilter sslFilter = (SslFilter)session.getFilterChain().get("sslFilter");
//            SSLSession sslSession = sslFilter.getSslSession(session);
//            Certificate[] certificates = sslSession.getPeerCertificates();
//            Log.e("Certificate",certificates[0].toString());
        }

        @Override
        public void sessionClosed(IoSession session) throws Exception {
            Log.e("Session Acceptor", "Session closed " +  session.getId());
        }

        @Override
        public void sessionIdle(IoSession session, IdleStatus status) throws Exception {

        }

        @Override
        public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
            cause.printStackTrace();
            session.close(true);
        }

        @Override
        public void messageReceived(IoSession session, Object message) throws Exception {

            final String receivedMessage = (String) message;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textView.setText(receivedMessage);
                }
            });

        }

        @Override
        public void messageSent(IoSession session, Object message) throws Exception {
            Log.e("Session", "Message send " +  session.getId() + " " + message.toString() );
        }

    };

    private class MinaTest extends AsyncTask<Void,Void,Void> {


        @Override
        protected Void doInBackground(Void... voids) {
            // make socket connector and connect to socket
            final NioSocketConnector connector = new NioSocketConnector();
            connector.setHandler(tcpHandlerConnector);
            connector.getSessionConfig().setKeepAlive(true);
            //TextLineCodecFactory will buffer incoming data and emit a message very time it finds a \n
            TextLineCodecFactory textLineFactory = new TextLineCodecFactory(Charset.defaultCharset(), LineDelimiter.UNIX, LineDelimiter.UNIX);
            textLineFactory.setDecoderMaxLineLength(512*1024); //Allow to receive up to 512kb of data
            connector.getFilterChain().addLast("codec", new ProtocolCodecFilter(textLineFactory));

            try {
                connector.getFilterChain().addFirst("sslFilter", getSslFilter(true));
            }catch (Exception e){
                e.printStackTrace();
            }


            final ConnectFuture future = connector.connect(new InetSocketAddress(remoteAddress.getText().toString(), PORT));
            future.awaitUninterruptibly();

            IoSession session = future.getSession();
//            session.setAttribute(SslFilter.DISABLE_ENCRYPTION_ONCE, Boolean.TRUE);
//            session.write("OK");
            if(((SslFilter)session.getFilterChain().get("sslFilter")).isSslStarted(session)){
                Log.e("SslSession", "Ssl started");
                session.write(editText.getText().toString());
                session.close(true);

            }else{
                Log.e("SslSession", "ssl not started");
                session.close(true);
            }


            return null;
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        acceptor.unbind();
    }


    public SslFilter getSslFilter(boolean isClient) {
        PrivateKey privateKey = null;

        SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            byte[] privateKeyBytes = Base64.decode(globalSettings.getString("privateKey", ""), 0);
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
        }catch (Exception e){
            e.printStackTrace();
            Log.e("KDE/Device","Exception");
        }
//
//        // refactor : setting public key. excetpion if device not paired
//        try {
//            SharedPreferences settings = getContext().getSharedPreferences(super.getDeviceId(), Context.MODE_PRIVATE);
//            byte[] publicKeyBytes = Base64.decode(settings.getString("publicKey", ""), 0);
//            publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
//        } catch (Exception e) {
//            e.printStackTrace();
//            Log.e("KDE/Device","Exception");
//        }
        try {

            byte[] certificateBytes = Base64.decode(globalSettings.getString("certificate", ""), 0);
            Log.e("getSslFilter/certificate bytes",certificateBytes.toString());

            X509CertificateHolder certificateHolder = new X509CertificateHolder(certificateBytes);
            X509Certificate certificate = new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(certificateHolder);
//            Log.e("getSslFilter/certificate", certificate.toString());

            TrustManagerFactory trustManagerFactory = BogusTrustManagerFactory.getInstance(BogusTrustManagerFactory.getDefaultAlgorithm());
//            Log.e("getSslFilter/trustmanager", trustManagerFactory.getTrustManagers().length + "");

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setKeyEntry("my_key_and_certificate",privateKey,"".toCharArray(),new Certificate[]{certificate});
//            Log.e("getSslFilter/keystore",keyStore.getKey("my_key_and_certificate","".toCharArray()).getEncoded().toString());
//            Log.e("getSslFilter/keystore",keyStore.getCertificate("my_key_and_certificate").toString());

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, "".toCharArray());
//            Log.e("getSslFilter/keymanagerfactory", keyManagerFactory.getKeyManagers().length + "");

            SSLContext tlsContext = SSLContext.getInstance("TLSv1.2");
            tlsContext.init(keyManagerFactory.getKeyManagers(), trustAllCerts, new SecureRandom());
            SslFilter filter = new SslFilter(tlsContext,true);

            if (isClient){
                filter.setUseClientMode(true);
            }else {
                filter.setUseClientMode(false);
            }

            filter.setWantClientAuth(true);
            return filter;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
}
