package taskerplugin.httpevent.receiver.com;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import taskerplugin.httpevent.Constants;
import taskerplugin.httpevent.TaskerPlugin;
import taskerplugin.httpevent.bundle.PluginBundleManager;
import taskerplugin.httpevent.receiver.BackgroundService;

/**
 * Created by Bidois Morgan on 23/04/15.
 */
public class HTTPHandler {

    private static Context context;
    private static String addr;
    private static String port;
    private MyHTTPD HTTPDServer;

    public HTTPHandler(Context c) throws IOException {
        // Initialize with defaut port, will be set after
        this.context = c;
    }

    public void setInformation(String addr, String port) {
        this.addr = addr;
        this.port = port;
    }

    public void initializeHTTPServer() {
//        this.HTTPDServer = new MyHTTPD(this.addr, this.port);
        this.HTTPDServer = new MyHTTPD(null, this.port);
    }

    public void start() {
        try {
            this.HTTPDServer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        this.HTTPDServer.closeAllConnections();
        this.HTTPDServer.stop();
    }

    private static final class MyHTTPD extends NanoHTTPD {

        public MyHTTPD(String addr, String port) {
            super(addr, Integer.parseInt(port));
        }

        @Override
        public NanoHTTPD.Response serve(String uri, NanoHTTPD.Method
                method, Map<String, String> headers, Map<String, String> parms,
                                        Map<String, String> files) {
            final StringBuilder buf = new StringBuilder();

            buf.append("Header<br/>");
            for (Map.Entry<String, String> kv : headers.entrySet()) {
                buf.append(kv.getKey() + " : " + kv.getValue() + "\n");
            }

            buf.append("<br/>----<br/>");

            buf.append("method = " + method + "<br/>");
            buf.append("uri = " + uri + "<br/>");

            buf.append("Params<br/>");
            for (Map.Entry<String, String> p : parms.entrySet()) {
                buf.append(p.getKey() + " : " + p.getValue() + "<br/>");
            }

            final String html = "<html><head><head><body><h1>Hello, World</h1></body>" + buf + "</html>";

            TaskerPlugin.Event.addPassThroughMessageID(BackgroundService.INTENT_REQUEST_REQUERY);

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

            // Adding HTTP Info
            try {
                o.put("httpAddr", addr);
                o.put("httpPort", port);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            Log.v(Constants.LOG_TAG, "JSON envoyé : " + o.toString());
            Bundle dataBundle = PluginBundleManager.generateURLBundle(context, o.toString());
            TaskerPlugin.Event.addPassThroughData(BackgroundService.INTENT_REQUEST_REQUERY, dataBundle);

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
            context.sendBroadcast(BackgroundService.INTENT_REQUEST_REQUERY);

            return new NanoHTTPD.Response(NanoHTTPD.Response.Status.OK, MIME_HTML, html);
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
}



