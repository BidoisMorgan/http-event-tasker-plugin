/*
 * Copyright 2013 two forty four a.m. LLC <http://www.twofortyfouram.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package taskerplugin.httpevent.receiver;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;


import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import taskerplugin.httpevent.Constants;
import taskerplugin.httpevent.TaskerPlugin;
import taskerplugin.httpevent.bundle.PluginBundleManager;
import taskerplugin.httpevent.ui.EditActivity;

/**
 * {@code Service} for monitoring the {@code REGISTERED_RECEIVER_ONLY} {@code Intent}s
 * {@link android.content.Intent#ACTION_SCREEN_ON} and {@link android.content.Intent#ACTION_SCREEN_OFF}.
 */
public final class BackgroundService extends Service {
    //@formatter:off
    /*
     * REPRESENTATION INVARIANTS:
     * - INTENT_REQUEST_REQUERY must not be mutated after it is initialized
     */
    //@formatter:on

    /**
     * {@code Intent} to ask Locale to re-query this plug-in condition.
     */
    /*
     * This object is cached here so that it only must be allocated once. The Activity name must be present as
     * an extra in this Intent, so that Locale will know who needs updating. Locale will ignore the requery
     * request unless the Activity extra is present.
     */
    protected static final Intent INTENT_REQUEST_REQUERY =
            new Intent(com.twofortyfouram.locale.Intent.ACTION_REQUEST_QUERY).putExtra(com.twofortyfouram.locale.Intent.EXTRA_ACTIVITY,
                    EditActivity.class.getName());


    /**
     * Server HTTP
     */
    private NanoHTTPD mHTTPD;

    private SocketIOHandler mSocketHandler;


    /**
     * Flag to note when {@link #onStartCommand(android.content.Intent, int, int)} has been called.
     */
    private boolean mIsOnStartCommandCalled = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(Constants.LOG_TAG, "CREATE SERVICE"); //$NON-NLS-1$

        /*
         * Listen continuously for screen Intents
         */
        try {
            WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

            final String formatedIpAddress = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff),
                    (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
            mHTTPD = new MyHTTPD(formatedIpAddress, getApplication());
//            mHTTPD.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mSocketHandler = new SocketIOHandler(getApplication());
//        mSocketHandler = new SocketIOHandler("http://thacthab.herokuapp.com", getApplication());
//        mSocketHandler.connect();
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        super.onStartCommand(intent, flags, startId);

        Log.v(Constants.LOG_TAG, "START SERVICE"); //$NON-NLS-1$

        /*
         * If the Intent is null, then the service is being started by Android rather than being started from
         * the BroadcastReceiver.
         */
        if (null != intent) {

            Bundle dataBundle = intent.getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE);
            String ssAddr = dataBundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_SOC_SERV_ADDR);
            String ssLogin = dataBundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_SOC_SERV_LOGIN);
            String ssPass = dataBundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_SOC_SERV_PASS);

            boolean resetSocketInfo = mSocketHandler.resetSocketInfo(ssAddr, ssLogin, ssPass);

            /*
             * Because Services are started from an event loop, there is a timing gap between when the
             * BroadcastReceiver checks the screen state and when the Service starts monitoring for screen
             * changes.
             *
             * This case is only important the first time the Service starts.
             */
            if (!mIsOnStartCommandCalled) {
//                TaskerPlugin.Event.addPassThroughMessageID(INTENT_REQUEST_REQUERY);
//                sendBroadcast(INTENT_REQUEST_REQUERY);
                try {
                    mHTTPD.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }


                Log.v(Constants.LOG_TAG, String.format("FISRT socket server : %s login : %s mdp : %s", ssAddr, ssLogin, ssPass)); //$NON-NLS-1$
                mSocketHandler.setServer(ssAddr);
                mSocketHandler.connect(ssLogin, ssPass);
            } else if (resetSocketInfo) {
                Log.v(Constants.LOG_TAG, String.format("RESET socket server : %s login : %s mdp : %s", ssAddr, ssLogin, ssPass)); //$NON-NLS-1$
                mSocketHandler.setServer(ssAddr);
                mSocketHandler.connect(ssLogin, ssPass);
            }

            ServiceWakeLockManager.releaseLock();
        }

        /*
         * It is OK for this to be set after the WakeLock is released.
         */
        mIsOnStartCommandCalled = true;

        return START_STICKY;
    }

    @Override
    public IBinder onBind(final Intent arg0) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mHTTPD.stop();
        mHTTPD = null;

        mSocketHandler.disconnect();
        mSocketHandler.close();
        mSocketHandler = null;
    }

    public String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        String ip = Formatter.formatIpAddress(inetAddress.hashCode());
                        Log.i(Constants.LOG_TAG, "***** IP=" + ip);
                        return ip;
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e(Constants.LOG_TAG, ex.toString());
        }
        return null;
    }

    private static String transformJSONtoStringURL(JSONObject objectParams) {
        String eq = "=";
        String separator = "&";
        String URLString = "";
        Iterator<?> keys = objectParams.keys();

        while (keys.hasNext()) {
            String key = (String) keys.next();

            try {
                // test JSON attribute
                JSONObject innerObject = new JSONObject((String) objectParams.get(key));
                URLString += transformJSONtoStringURL(innerObject);
            } catch (JSONException e) {
                // If no JSON, consider as a String
                try {
                    URLString += key + eq + objectParams.get(key).toString();
                } catch (JSONException ex) {
                    ex.printStackTrace();
                }
            }

            if (keys.hasNext()) {
                URLString += separator;
            }

        }

        Log.v(Constants.LOG_TAG, "URL String built = " + URLString); //$NON-NLS-1$
        return URLString;
    }

    private static final class MyHTTPD extends NanoHTTPD {

        private static final int PORT = 8765;
        private Context context;

        public MyHTTPD(String ipAddr, Context c) throws IOException {
            super(ipAddr, PORT);
            context = c;
        }

        @Override
        public Response serve(String uri, Method method, Map<String, String> headers, Map<String, String> parms,
                              Map<String, String> files) {
            final StringBuilder buf = new StringBuilder();

            buf.append("Header<br>");
            for (Map.Entry<String, String> kv : headers.entrySet()) {
                buf.append(kv.getKey() + " : " + kv.getValue() + "\n");
            }

            buf.append("<br>----<br>");

            buf.append("method = " + method + "<br>");
            buf.append("uri = " + uri + "<br>");

            buf.append("Params<br>");
            for (Map.Entry<String, String> p : parms.entrySet()) {
                buf.append(p.getKey() + " : " + p.getValue() + "<br>");
            }

            final String html = "<html><head><head><body><h1>Hello, World</h1></body>" + buf + "</html>";

            TaskerPlugin.Event.addPassThroughMessageID(INTENT_REQUEST_REQUERY);

            HashMap<String, String> mapBis = new HashMap<>(parms);
            mapBis.remove("NanoHttpd.QUERY_STRING");

            JSONObject o = new JSONObject();
            for (Map.Entry<String, String> param : mapBis.entrySet()) {
                try {
                    o.put(param.getKey(), param.getValue());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            Log.v(Constants.LOG_TAG, "JSON envoyé : " + o.toString());
            Bundle dataBundle = PluginBundleManager.generateURLBundle(context, o.toString());
            TaskerPlugin.Event.addPassThroughData(INTENT_REQUEST_REQUERY, dataBundle);

             /*
             * Ask Locale to re-query our condition instances. Note: this plug-in does not keep track of what
             * types of conditions have been set up. While executing this code, this Condition has no idea
             * whether there are even any Display conditions within Locale or whether those conditions are
             * checking for screen on versus screen off. This is an intentional design decision to eliminate
             * all sorts of complex synchronization problems between state within Locale and state within the
             * plug-in.
             */

            /*
             * Note: For the most part, the Display condition service will only be running if there is an
             * instance of the Display condition within Locale. The only time this wouldn't be true is if the
             * user creates a Display condition and saves it, Locale queries the Display condition (therefore
             * launching its service), and then the Locale user deletes the Display condition or the situation
             * containing the Display condition. At this point, the Display condition service will be left
             * running. This is not considered to be a significant problem, because Android will kill the
             * Display condition service when necessary. And because Locale runs with foreground priority (it
             * displays a notification), this abandoned Display condition service will be killed before
             * Locale.
             */
            context.sendBroadcast(INTENT_REQUEST_REQUERY);

            return new Response(Response.Status.OK, MIME_HTML, html);
        }

//        @Override
//        public NanoHTTPD.Response serve(NanoHTTPD.IHTTPSession session) {
//            final StringBuilder buf = new StringBuilder();
//
//            buf.append("Header\n");
//            for (Map.Entry<String, String> kv : session.getHeaders().entrySet()) {
//                buf.append(kv.getKey() + " : " + kv.getValue() + "\n");
//            }
//
//            buf.append("----\n");
//
//            buf.append("method = " + session.getMethod() + "\n");
//            buf.append("uri = " + session.getUri() + "\n");
//
//            buf.append("Params :\n");
//            for (Map.Entry<String, String> p : session.getParms().entrySet()) {
//                buf.append(p.getKey() + " : " + p.getValue() + "\n");
//            }
//
//            final String html = "<html><head><head><body><h1>Hello, World</h1></body>" + buf + "</html>";
//
//            TaskerPlugin.Event.addPassThroughMessageID(INTENT_REQUEST_REQUERY);
//
//            TreeMap<String, String> tree = new TreeMap<String, String>(session.getParms());
//
////            Bundle dataBundle = PluginBundleManager.generateURLBundle(context, tree.get(tree.lastKey()));
////            TaskerPlugin.Event.addPassThroughData(INTENT_REQUEST_REQUERY, dataBundle);
//
//            context.sendBroadcast(INTENT_REQUEST_REQUERY);
//
//            return new NanoHTTPD.Response(NanoHTTPD.Response.Status.OK, MIME_HTML, html);
//        }

    }

    private static final class SocketIOHandler {

        private com.github.nkzawa.socketio.client.Socket socket;
        private Context context;
        private String host;
        private String login;
        private String pass;

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

                    Log.v(Constants.LOG_TAG, "JSON envoyé : " + o.toString());
                    Bundle dataBundle = PluginBundleManager.generateURLBundle(context, o.toString());

                    TaskerPlugin.Event.addPassThroughData(INTENT_REQUEST_REQUERY, dataBundle);
                    TaskerPlugin.Event.addPassThroughMessageID(INTENT_REQUEST_REQUERY);
                    context.sendBroadcast(INTENT_REQUEST_REQUERY);
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
}