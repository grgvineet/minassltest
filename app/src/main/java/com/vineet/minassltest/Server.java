package com.vineet.minassltest;

import android.support.v4.util.LongSparseArray;
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

import org.apache.mina.core.future.WriteFuture;
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
import java.util.ArrayList;
import java.util.HashMap;


public class Server extends ActionBarActivity {

    boolean binded = false;

    Button bind, unbind, send;
    EditText port, textToSend;
    TextView messageReceived;

    NioSocketAcceptor acceptor;
    HashMap<Long,IoSession> nioSessions = new HashMap<>();

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
        send.setEnabled(false);

        acceptor = new NioSocketAcceptor();
        acceptor.setHandler(tcpHandler);
        acceptor.getSessionConfig().setKeepAlive(true);
        acceptor.getSessionConfig().setReuseAddress(true);
        TextLineCodecFactory textLineFactory = new TextLineCodecFactory(Charset.defaultCharset(), LineDelimiter.UNIX, LineDelimiter.UNIX);
        textLineFactory.setDecoderMaxLineLength(512 * 1024); //Allow to receive up to 512kb of data
        acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(textLineFactory));


        bind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                    if (!binded) {

                        acceptor.bind(new InetSocketAddress(Integer.parseInt(port.getText().toString())));
                        bind.setEnabled(false);
                        unbind.setEnabled(true);
                        send.setEnabled(true);
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
//                    acceptor.dispose();
                    bind.setEnabled(true);
                    unbind.setEnabled(false);
                    send.setEnabled(false);
                    binded = false;
                }
            }
        });

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        for (IoSession session : nioSessions.values()){
                            WriteFuture future = session.write(textToSend.getText().toString());
                            future.awaitUninterruptibly();
                            if (!future.isWritten()) {
                                Log.e("KDE/sendPackage", "!future.isWritten()");
                                return;
                            }
                        }
                    }
                }).start();
            }
        });


    }

    private IoHandler tcpHandler = new IoHandlerAdapter() {
        @Override
        public void sessionCreated(final IoSession session) throws Exception {
            Log.e("Server", "Session created " + session.getId());
            session.getFilterChain().addFirst("sslFilter", SslFIlter.getSslFilter(Server.this, false));
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Server.this,"Connected to :" + session.getRemoteAddress().toString(), Toast.LENGTH_LONG).show();
                }
            });
        }

        @Override
        public void sessionOpened(IoSession session) throws Exception {
            Log.e("Server", "Session opened " + session.getId());
            nioSessions.put(session.getId(), session);
        }

        @Override
        public void sessionClosed(IoSession session) throws Exception {
            Log.e("Server", "Session closed " +  session.getId());
            nioSessions.remove(session.getId());
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
            Log.e("Server","Message Received :" + message.toString());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    messageReceived.setText("Message Received :" +receivedMessage);
                }
            });


        }

        @Override
        public void messageSent(IoSession session, Object message) throws Exception {
            Log.e("Server", "Message send " +  session.getId() + " " + (String)message );
        }

    };


    @Override
    public void finish() {
        super.finish();
        acceptor.dispose();
    }

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
