package com.vineet.minassltest;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Date;


public class MainActivity extends ActionBarActivity {


    Button server, client;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        server = (Button) findViewById(R.id.server);
        client = (Button)findViewById(R.id.client);

        server.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), Server.class);
                startActivity(intent);
            }
        });

        client.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), Client.class);
                startActivity(intent);
            }
        });
        initializeRsaKeys();
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
    }



}
