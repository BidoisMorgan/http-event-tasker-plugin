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
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Iterator;

import taskerplugin.httpevent.Constants;
import taskerplugin.httpevent.bundle.PluginBundleManager;
import taskerplugin.httpevent.receiver.com.HTTPHandler;
import taskerplugin.httpevent.receiver.com.NetworkStateReceiver;
import taskerplugin.httpevent.receiver.com.SocketIOHandler;
import taskerplugin.httpevent.ui.EditActivity;

/**
 * {@code Service} for monitoring the {@code REGISTERED_RECEIVER_ONLY} {@code Intent}s
 * {@link android.content.Intent#ACTION_SCREEN_ON} and {@link android.content.Intent#ACTION_SCREEN_OFF}.
 */
public final class BackgroundService extends Service implements NetworkStateReceiver.NetworkStateReceiverListener {
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
    public static final Intent INTENT_REQUEST_REQUERY =
            new Intent(com.twofortyfouram.locale.Intent.ACTION_REQUEST_QUERY).putExtra(com.twofortyfouram.locale.Intent.EXTRA_ACTIVITY,
                    EditActivity.class.getName());


    /**
     * Server HTTP
     */
    private HTTPHandler mHTTPDHandler;

    private SocketIOHandler mSocketHandler;

    private NetworkStateReceiver networkStateReceiver;


    /**
     * Flag to note when {@link #onStartCommand(android.content.Intent, int, int)} has been called.
     */
    private boolean mIsOnStartCommandCalled = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(Constants.LOG_TAG, "CREATE SERVICE"); //$NON-NLS-1$

        networkStateReceiver = new NetworkStateReceiver();
        networkStateReceiver.addListener(this);
        this.registerReceiver(networkStateReceiver, new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION));

        /*
         * Listen continuously for screen Intents
         */
        try {
            WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

            final String formatedIpAddress = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff),
                    (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));


            mHTTPDHandler = new HTTPHandler(getApplication());
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

            String httpAddr = dataBundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_HTTP_SERV_ADDR);
            String httpPort = dataBundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_HTTP_SERV_PORT);

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
                mHTTPDHandler.setInformation(httpAddr, httpPort);
                mHTTPDHandler.initializeHTTPServer();
                mHTTPDHandler.start();

                Log.v(Constants.LOG_TAG, String.format("FISRT socket server : %s login : %s mdp : %s", ssAddr, ssLogin, ssPass)); //$NON-NLS-1$
                mSocketHandler.setServer(ssAddr);
                mSocketHandler.addHTTPInfo(httpAddr, httpPort);
                mSocketHandler.connect(ssLogin, ssPass);
            } else if (resetSocketInfo) {
                Log.v(Constants.LOG_TAG, String.format("RESET socket server : %s login : %s mdp : %s", ssAddr, ssLogin, ssPass)); //$NON-NLS-1$
                mSocketHandler.addHTTPInfo(httpAddr, httpPort);
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

        mHTTPDHandler.stop();
        mHTTPDHandler = null;

        mSocketHandler.socketOff();
        mSocketHandler.disconnect();
        mSocketHandler.close();
        mSocketHandler = null;

        networkStateReceiver.removeListener(this);
        this.unregisterReceiver(networkStateReceiver);
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


    @Override
    public void networkAvailable() {
//        if (!mSocketHandler.isConnected()) {
//            mSocketHandler.resetServer();
//            mSocketHandler.connect();
//        }
        Log.v(Constants.LOG_TAG, "******Network available******"); //$NON-NLS-1$
    }

    @Override
    public void networkUnavailable() {
//        mHTTPDHandler.stop();
//        mSocketHandler.disconnect();
        Log.v(Constants.LOG_TAG, "******Network unAvailable******"); //$NON-NLS-1$
    }
}