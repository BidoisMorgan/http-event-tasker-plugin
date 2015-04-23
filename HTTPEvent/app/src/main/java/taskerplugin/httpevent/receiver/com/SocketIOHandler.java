package taskerplugin.httpevent.receiver.com;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import taskerplugin.httpevent.Constants;
import taskerplugin.httpevent.TaskerPlugin;
import taskerplugin.httpevent.bundle.PluginBundleManager;
import taskerplugin.httpevent.receiver.BackgroundService;

/**
 * Created by Bidois Morgan on 23/04/15.
 */
public final class SocketIOHandler {

    private com.github.nkzawa.socketio.client.Socket socket;
    private Context context;
    private String host;
    private String login;
    private String pass;

    private String httpAddr;
    private String httpPort;

    public SocketIOHandler(String host, Context ctxt) {
        try {
            this.context = ctxt;
            // Initialize socket with the host
            this.socket = IO.socket(host);
            Log.v(Constants.LOG_TAG, "Constructeur socket handler"); //$NON-NLS-1$
            this.configureSocket(socket);

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public SocketIOHandler(Context ctxt) {
        this.context = ctxt;
        // Initialize socket with the host
        Log.v(Constants.LOG_TAG, "Constructeur socket handler"); //$NON-NLS-1$
//            this.configureSocket(socket);
    }

    public void setServer(String host) {
        if (this.host == null) {
            this.host = host;
            try {
                this.socket = IO.socket(this.host);
                this.configureSocket(socket);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        } else {
            if (this.socket.connected()) {
                this.socket.disconnect();
            }
            try {
                this.socket = IO.socket(this.host);
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

    public void addHTTPInfo(String addr, String port) {
        this.httpAddr = addr;
        this.httpPort = port;
    }

    public void connect(String login, String pass) {
        this.login = login;
        this.pass = pass;

        if (!this.socket.connected()) {
            Log.v(Constants.LOG_TAG, "connect()"); //$NON-NLS-1$
            this.socket.connect();
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
    }

    public void disconnect() {
        this.socket.disconnect();
    }

    public void close() {
        this.socket.close();
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

    private void configureSocket(final Socket socket) {
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                Log.v(Constants.LOG_TAG, "Socket IO -> Connect "); //$NON-NLS-1$
            }

        }).on("all", new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                Log.v(Constants.LOG_TAG, "Socket IO -> Event handle "); //$NON-NLS-1$
                JSONObject o = (JSONObject) args[0];

                // Adding HTTP Info
                try {
                    o.put("httpAddr", httpAddr);
                    o.put("httpPort", httpPort);
                } catch (JSONException e) {
                    e.printStackTrace();
                }


                Log.v(Constants.LOG_TAG, "JSON envoyé : " + o.toString());
                Bundle dataBundle = PluginBundleManager.generateURLBundle(context, o.toString());

                TaskerPlugin.Event.addPassThroughData(BackgroundService.INTENT_REQUEST_REQUERY, dataBundle);
                TaskerPlugin.Event.addPassThroughMessageID(BackgroundService.INTENT_REQUEST_REQUERY);
                context.sendBroadcast(BackgroundService.INTENT_REQUEST_REQUERY);
            }

        }).on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                Log.v(Constants.LOG_TAG, "Socket IO -> Connect Error "); //$NON-NLS-1$
            }

        }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                Log.v(Constants.LOG_TAG, "Socket IO -> Disconnect "); //$NON-NLS-1$
                socket.off();
            }

        });
    }

    public boolean resetSocketInfo(String newAddr,String newLogin, String newPass) {
        // If no login/pass no reset
        if (newLogin.equals("") && newPass.equals("")) {
            return false;
        }
        if (this.login == null && this.pass == null) {
            return true;
        }
        return !newLogin.equals(this.login) || !newPass.equals(this.pass) || !newAddr.equals(this.host);
    }

}
