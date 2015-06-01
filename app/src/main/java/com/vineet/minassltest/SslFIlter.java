package com.vineet.minassltest;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import org.apache.mina.filter.ssl.SslFilter;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Created by vineet on 8/5/15.
 */
public class SslFIlter {

    public static SslFilter getSslFilter(Context context,boolean isClient) {
        PrivateKey privateKey = null;

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

        SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            byte[] privateKeyBytes = Base64.decode(globalSettings.getString("privateKey", ""), 0);
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
        }catch (Exception e){
            e.printStackTrace();
            Log.e("KDE/Device", "Exception");
        }

        try {

            byte[] certificateBytes = Base64.decode(globalSettings.getString("certificate", ""), 0);

            X509CertificateHolder certificateHolder = new X509CertificateHolder(certificateBytes);
            X509Certificate certificate = new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(certificateHolder);

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setKeyEntry("my_key_and_certificate",privateKey,"".toCharArray(),new Certificate[]{certificate});

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, "".toCharArray());

            SSLContext tlsContext = SSLContext.getInstance("TLSv1.2");
            tlsContext.init(keyManagerFactory.getKeyManagers(), trustAllCerts, new SecureRandom());
            SslFilter filter = new SslFilter(tlsContext,true);

            if (isClient){
                filter.setUseClientMode(true);
            }else {
                filter.setUseClientMode(false);
                filter.setWantClientAuth(true);
            }

            return filter;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
}
