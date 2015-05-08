package com.vineet.minassltest;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;


public class Server extends ActionBarActivity {

    boolean binded = false;

    Button bind, unbind, send;
    EditText port, textToSend;
    TextView messageReceived;

    NioSocketAcceptor acceptor;
    IoSession ioSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        bind = (Button)findViewById(R.id.bBind);
        unbind = (Button)findViewById(R.id.bUnbind);
        send = (Button)findViewById(R.id.bSSend);

        port = (EditText)findViewById(R.id.etSPort);
        textToSend = (EditText)findViewById(R.id.etSTextToSend);

        messageReceived = (TextView)findViewById(R.id.tvSMessageReceived);

        unbind.setEnabled(false);

        acceptor = new NioSocketAcceptor();
        acceptor.setHandler(tcpHandler);
        acceptor.getSessionConfig().setKeepAlive(true);
        acceptor.getSessionConfig().setReuseAddress(true);
        TextLineCodecFactory textLineFactory = new TextLineCodecFactory(Charset.defaultCharset(), LineDelimiter.UNIX, LineDelimiter.UNIX);
        textLineFactory.setDecoderMaxLineLength(512*1024); //Allow to receive up to 512kb of data
        acceptor.getFilterChain().addLast("sslFilter", SslFIlter.getSslFilter(Server.this, false));
        acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(textLineFactory));

        bind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                    if (!binded) {
                        acceptor.bind(new InetSocketAddress(Integer.parseInt(port.getText().toString())));
                        bind.setEnabled(false);
                        unbind.setEnabled(true);
                        binded = true;
                    }
                }catch (Exception e){
                    Toast.makeText(Server.this,"Error binding port",Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            }
        });

        unbind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (binded) {
                    acceptor.unbind();
                    bind.setEnabled(true);
                    unbind.setEnabled(false);
                    binded = false;
                }
            }
        });

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                    if (ioSession != null){
                        ioSession.write(textToSend.getText().toString());
                    }
            }
        });


    }

    private IoHandler tcpHandler = new IoHandlerAdapter() {
        @Override
        public void sessionCreated(IoSession session) throws Exception {
            Log.e("Session Connector", "Session created " + session.getId());

        }

        @Override
        public void sessionOpened(IoSession session) throws Exception {
            Log.e("Session Connector", "Session opened " + session.getId());
//            ((SslFilter)session.getFilterChain().get("sslFilter")).startSsl(session);
            ioSession = session;
        }

        @Override
        public void sessionClosed(IoSession session) throws Exception {
            Log.e("Session Connector", "Session closed " +  session.getId());
            ioSession = null;
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
            Log.e("Message received", message.toString());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    messageReceived.setText("Message Received :" +receivedMessage);
                }
            });


        }

        @Override
        public void messageSent(IoSession session, Object message) throws Exception {
            Log.e("Session Connector", "Message send " +  session.getId() + " " + (String)message );
        }

    };


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_server, menu);
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
}
