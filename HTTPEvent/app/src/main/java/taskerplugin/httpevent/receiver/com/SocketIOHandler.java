package taskerplugin.httpevent.receiver.com;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import taskerplugin.httpevent.Constants;
import taskerplugin.httpevent.TaskerPlugin;
import taskerplugin.httpevent.bundle.PluginBundleManager;
import taskerplugin.httpevent.receiver.BackgroundService;

/**
 * Created by Bidois Morgan on 23/04/15.
 */
public final class SocketIOHandler {

    private Socket socket;
    private Context context;
    private String host;
    private String login;
    private String pass;

    private String httpAddr;
    private String httpPort;

    private final JSONObject socketStateCreation        = new JSONObject();
    private final JSONObject socketStateConnection      = new JSONObject();
    private final JSONObject socketStateDisconnection   = new JSONObject();
    private final JSONObject socketErrorConnection      = new JSONObject();

    private Timer timer;
    private PingSocketTask pingSocTask;

    public SocketIOHandler(String host, Context ctxt) {
        try {
            this.context = ctxt;
            this.timer = null;
            // Initialize socket with the host
            this.socket = IO.socket(host);
            Log.v(Constants.LOG_TAG, "Constructeur socket handler"); //$NON-NLS-1$
            this.configureSocket(socket);

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public SocketIOHandler(Context ctxt) {
        this.timer = null;
        this.context = ctxt;
        this.createSockeStateMsg();
        Log.v(Constants.LOG_TAG, "Constructeur socket handler"); //$NON-NLS-1$
    }

    private final void createSockeStateMsg() {
        try {
            socketStateCreation.put("creation", true);
            socketStateCreation.put("socketstate", "creation");
            socketStateConnection.put("connection", true);
            socketStateConnection.put("socketstate", "connection");
            socketStateDisconnection.put("disconnection", true);
            socketStateDisconnection.put("socketstate", "disconnection");
            socketErrorConnection.put("connectionError", true);
            socketErrorConnection.put("socketstate", "connectionError");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setServer(String host) {
        /** SSL **/
        SSLContext sc = null;
        try {
            sc = SSLContext.getInstance("TLS");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        try {
            sc.init(null, trustAllCerts, new SecureRandom());
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        IO.setDefaultSSLContext(sc);
        HttpsURLConnection.setDefaultHostnameVerifier(new RelaxedHostNameVerifier());

        // socket options
        IO.Options opts = new IO.Options();
        opts.forceNew = true;
        opts.reconnection = true;

        if (host.split(":")[0].equals("https")) {
            opts.secure = true;
        } else {
            opts.secure = false;
        }
        opts.sslContext = sc;

        if (this.host == null) {
            this.host = host;
            try {
                this.socket = IO.socket(this.host, opts);
                this.configureSocket(socket);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        } else {
            if (this.socket.connected()) {
                this.socket.disconnect();
            }
            try {
                this.socket = IO.socket(this.host, opts);
                this.configureSocket(socket);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }

            /* else if (!this.host.equals(host)) {
                if (this.socket.connected()) {
                    this.socket.disconnect();
                }
                try {
                    this.socket = IO.socket(this.host);
                    this.configureSocket(socket);
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            } */
    }

    public void resetServer() {
        this.setServer(this.host);
    }

    public void addHTTPInfo(String addr, String port) {
        this.httpAddr = addr;
        this.httpPort = port;
    }

    public void connect(String login, String pass) {
        this.login = login;
        this.pass = pass;

        if (this.socket != null && !this.socket.connected()) {
            Log.v(Constants.LOG_TAG, "connect()"); //$NON-NLS-1$
            this.socket.connect();
        }
    }

    public void identifyAndSubscribe() {
        this.identify(login, pass);

        JSONObject subscribe = new JSONObject();
        JSONObject data = new JSONObject();
        try {
            subscribe.put("id", "all");
            data.put("title", ".*");
            data.put("regexp", true);
            subscribe.put("data", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.socket.emit("subscribe", subscribe);
    }

    public void connect() {
        this.connect(this.login, this.pass);
    }

    /**
     * TODO A mettre dans interface
     *
     * @param msg
     */
    private void sendMsgToTasker(String msg) {
//        Log.v(Constants.LOG_TAG, "JSON envoyé : " + msg);
        Bundle dataBundle = PluginBundleManager.generateURLBundle(context, msg);

        TaskerPlugin.Event.addPassThroughData(BackgroundService.INTENT_REQUEST_REQUERY, dataBundle);
        TaskerPlugin.Event.addPassThroughMessageID(BackgroundService.INTENT_REQUEST_REQUERY);
        context.sendBroadcast(BackgroundService.INTENT_REQUEST_REQUERY);
    }

    public void disconnect() {
        this.socket.disconnect();
    }

    public void close() {
        this.socket.close();
    }

    public boolean isConnected() {
        return this.socket.connected();
    }

    private void identify(String login, String pass) {
        JSONObject loginObject = new JSONObject();
        try {
            loginObject.put("login", login);
            loginObject.put("pass", pass);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        this.socket.emit("login", loginObject);
    }

    private void cancelTimer() {
        if(this.timer != null) {
            this.timer.cancel();
            this.timer = null;
        }
    }
    private void initTimer(final Socket socket) {
        initTimer(socket, 20000);
    }
    private void initTimer(final Socket socket, long delay) {
        this.cancelTimer();
        this.timer = new Timer();
        timer.schedule(new PingSocketTask(socket), delay, delay);
    }

    private void configureSocket(final Socket socket) {
        socket.off();
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                Log.v(Constants.LOG_TAG, "Socket IO " + socket.id() + " -> Connect "); //$NON-NLS-1$
                identifyAndSubscribe();
                // Connection Socket message
                sendMsgToTasker(socketStateConnection.toString());

                initTimer(socket);
                /*timer = new Timer();
                if (pingSocTask == null) {
                    pingSocTask = new PingSocketTask(socket);
                    timer.schedule(pingSocTask, 20000, 20000);
                }*/
            }

        }).on("all", new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                Log.v(Constants.LOG_TAG, "Socket IO -> Event handle "); //$NON-NLS-1$
                JSONObject o;
                try {
                    o = (JSONObject) args[0];
                } catch (ClassCastException ex) {
                    o = new JSONObject();
                    try {
                        o.put("error", "malformated message");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                // Adding HTTP Info
                try {
                    o.put("httpAddr", httpAddr);
                    o.put("httpPort", httpPort);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                Log.v(Constants.LOG_TAG, "JSON reçu transféré to tasker : " + o.toString());
                sendMsgToTasker(o.toString());

//                Log.v(Constants.LOG_TAG, "JSON envoyé : " + o.toString());
//                Bundle dataBundle = PluginBundleManager.generateURLBundle(context, o.toString());
//
//                TaskerPlugin.Event.addPassThroughData(BackgroundService.INTENT_REQUEST_REQUERY, dataBundle);
//                TaskerPlugin.Event.addPassThroughMessageID(BackgroundService.INTENT_REQUEST_REQUERY);
//                context.sendBroadcast(BackgroundService.INTENT_REQUEST_REQUERY);
            }
        }).on("ping", new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                Log.v(Constants.LOG_TAG, "PONG");
                socket.emit("pong");
            }

        }).on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                Log.v(Constants.LOG_TAG, "Socket IO -> Connect Error "); //$NON-NLS-1$
                cancelTimer();
                sendMsgToTasker(socketErrorConnection.toString());
            }

        }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                Log.v(Constants.LOG_TAG, "Socket IO -> Disconnect "); //$NON-NLS-1$
                // Disconnection Socket message
                Log.v(Constants.LOG_TAG, "JSON disconnection socket : " + socketStateDisconnection.toString());
                sendMsgToTasker(socketStateDisconnection.toString());

                cancelTimer();
                /*if (timer != null) {
                    pingSocTask.cancel();
                    pingSocTask = null;
                    timer.cancel();
                    timer = null;
                }*/
            }

        }).on(Socket.EVENT_RECONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                Log.v(Constants.LOG_TAG, "Socket IO -> Reconnection "); //$NON-NLS-1$
                identifyAndSubscribe();

                initTimer(socket);
                /*timer = new Timer();
                pingSocTask = new PingSocketTask(socket);
                timer.schedule(pingSocTask, 20000, 20000);*/
            }

        });

        // Creation Socket message
        Log.v(Constants.LOG_TAG, "JSON creation socket : " + socketStateCreation.toString());
        sendMsgToTasker(socketStateCreation.toString());
    }

    public void socketOff() {
        this.socket.off();
    }

    public boolean resetSocketInfo(String newAddr, String newLogin, String newPass) {
        // If no login/pass no reset
        if (newLogin.equals("") && newPass.equals("")) {
            return false;
        }
        if (this.login == null && this.pass == null) {
            return true;
        }
        return !newLogin.equals(this.login) || !newPass.equals(this.pass) || !newAddr.equals(this.host);
    }

    private class PingSocketTask extends TimerTask {

        private Socket socket;

        public PingSocketTask(Socket soc) {
            this.socket = soc;
        }

        @Override
        public void run() {
            Log.v(Constants.LOG_TAG, "Ping!");
            socket.emit("ping", true);
        }
    }

    /*** SSL ***/
    public static class RelaxedHostNameVerifier implements HostnameVerifier {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }

    private TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[]{};
        }

        public void checkClientTrusted(X509Certificate[] chain,
                                       String authType) throws CertificateException {
        }

        public void checkServerTrusted(X509Certificate[] chain,
                                       String authType) throws CertificateException {
        }
    }};

}
