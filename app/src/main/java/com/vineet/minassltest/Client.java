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

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketConnector;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;


public class Client extends ActionBarActivity {

    Button connect, disconnect, send;
    EditText port, remoteAddress, textToSend;
    TextView messageReceived;

    NioSocketConnector socketConnector;
    IoSession ioSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);

        connect = (Button)findViewById(R.id.bConnect);
        disconnect = (Button)findViewById(R.id.bDisconnect);
        send = (Button)findViewById(R.id.bCSend);

        port = (EditText)findViewById(R.id.etCPort);
        remoteAddress = (EditText)findViewById(R.id.etRemoteAddress);
        textToSend = (EditText)findViewById(R.id.etCTextToSend);

        messageReceived = (TextView)findViewById(R.id.tvCMessageReceived);

        disconnect.setEnabled(false);
        send.setEnabled(false);

        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                connect.setEnabled(false);
                disconnect.setEnabled(true);
                send.setEnabled(true);

                socketConnector = new NioSocketConnector();
                socketConnector.setHandler(tcpHandler);
                socketConnector.getSessionConfig().setKeepAlive(true);
                socketConnector.getSessionConfig().setReuseAddress(true);
                //TextLineCodecFactory will buffer incoming data and emit a message very time it finds a \n
                final TextLineCodecFactory textLineFactory = new TextLineCodecFactory(Charset.defaultCharset(), LineDelimiter.UNIX, LineDelimiter.UNIX);
                textLineFactory.setDecoderMaxLineLength(512 * 1024); //Allow to receive up to 512kb of data
                socketConnector.getFilterChain().addLast("codec", new ProtocolCodecFilter(textLineFactory));

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final ConnectFuture future = socketConnector.connect(new InetSocketAddress("192.168.1." + remoteAddress.getText().toString(), Integer.parseInt(port.getText().toString())));
                        future.addListener(new IoFutureListener<IoFuture>() {

                            @Override
                            public void operationComplete(IoFuture ioFuture) {
                                try {
                                    future.removeListener(this);
                                    final IoSession session = ioFuture.getSession();
                                    Log.i("Client", "Connection successful: " + session.isConnected());
                                    if (session.isConnected()) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(Client.this, "Session connected", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }else{
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(Client.this, "Can't connect", Toast.LENGTH_SHORT).show();
                                                disconnect.setEnabled(false);
                                                connect.setEnabled(true);
                                                send.setEnabled(false);
                                            }
                                        });
                                    }

                                } catch (Exception e) { //If we don't catch it here, Mina will swallow it :/
                                    e.printStackTrace();
                                    Log.e("Client", "sessionClosed exception");
                                }
                            }

                        });
                    }
                }).start();

            }
        });

        disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                disconnect.setEnabled(false);
                connect.setEnabled(true);
                send.setEnabled(false);

                socketConnector.dispose();

            }
        });

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        WriteFuture future = ioSession.write(textToSend.getText().toString());
                        future.awaitUninterruptibly();
                        if (!future.isWritten()) {
                            Log.e("Client", "!future.isWritten()");
                            return;
                        }
                    }
                }).start();
            }
        });

    }

    private IoHandler tcpHandler = new IoHandlerAdapter() {
        @Override
        public void sessionCreated(final IoSession session) throws Exception {
            Log.e("Client", "Session created " + session.getId());
            ioSession = session;
            ioSession.getFilterChain().addFirst("sslFilter", SslFIlter.getSslFilter(Client.this,true));

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Client.this, "Connected to :" + session.getRemoteAddress().toString(), Toast.LENGTH_LONG).show();
                }
            });

        }

        @Override
        public void sessionOpened(IoSession session) throws Exception {
            Log.e("Client", "Session opened " + session.getId());
        }

        @Override
        public void sessionClosed(IoSession session) throws Exception {
            Log.e("Client", "Session closed " +  session.getId());
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
        public void messageReceived(IoSession session, final Object message) throws Exception {
            Log.e("Client", "Message received " + message.toString());
            final String receivedMessage = (String) message;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    messageReceived.setText("Message Received :" + receivedMessage);
                }
            });

        }

        @Override
        public void messageSent(IoSession session, Object message) throws Exception {
            Log.e("Client", "Message send " +  session.getId() + " " + message.toString() );
        }

    };



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_client, menu);
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
