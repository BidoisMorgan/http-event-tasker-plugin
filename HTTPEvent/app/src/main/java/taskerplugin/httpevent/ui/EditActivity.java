package taskerplugin.httpevent.ui;

import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;

import taskerplugin.httpevent.R;
import taskerplugin.httpevent.bundle.PluginBundleManager;

/**
 * Created by Bidois Morgan on 01/04/15.
 */
public class EditActivity extends AbstractPluginActivity {

    private static int PORT = 8765;
    private EditText editName;
    private EditText editFilters;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_activity);

        TextView txtAddr = (TextView) findViewById(R.id.txt_addr);
        txtAddr.setText(getFormatedAddress());

        TextView txtPort = (TextView) findViewById(R.id.txt_port);
        txtPort.setText(Integer.toString(PORT));

        editName = (EditText) findViewById(R.id.edit_txt_name);
        editFilters = (EditText) findViewById(R.id.edit_txt_filters);

        if (getIntent().getStringExtra(com.twofortyfouram.locale.Intent.EXTRA_STRING_BLURB) != null) {
            editName.setText(getIntent().getStringExtra(com.twofortyfouram.locale.Intent.EXTRA_STRING_BLURB));
        }

        if (getIntent().getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE) != null) {
            ArrayList<String> filters = getIntent().getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE).getStringArrayList(PluginBundleManager.BUNDLE_EXTRA_STRINGS_FILTERS);
            if (filters != null) {
                String stringFilters = "";
                for (String f : filters) {
                    stringFilters += f + "\n";
                }
                editFilters.setText(stringFilters);
            }
        }
    }

    @Override
    public void finish() {
        final Intent resultIntent = new Intent();

        String eventName = "Default";
        ArrayList<String> filters = new ArrayList<>();

        if (!editName.getText().toString().equals("")) {
            eventName = String.valueOf(editName.getText());
        }

        if (!editFilters.getText().toString().equals("")) {
            filters = new ArrayList<>(Arrays.asList(editFilters.getText().toString().split("[\\r\\n]+")));
        }

        /*
         * This extra is the data to ourselves: either for the Activity or the BroadcastReceiver. Note
         * that anything placed in this Bundle must be available to Locale's class loader. So storing
         * String, int, and other standard objects will work just fine. Parcelable objects are not
         * acceptable, unless they also implement Serializable. Serializable objects must be standard
         * Android platform objects (A Serializable class private to this plug-in's APK cannot be
         * stored in the Bundle, as Locale's classloader will not recognize it).
         */
        final Bundle resultBundle = PluginBundleManager.generateBundle(getApplicationContext(), filters);
        resultIntent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE, resultBundle);

        /*
         * The blurb is concise status text to be displayed in the host's UI.
         */
        resultIntent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_STRING_BLURB, eventName);

        setResult(RESULT_OK, resultIntent);

        super.finish();
    }

    /**
     * Function to get the current IP Address of the device
     *
     * @return IP Address device formated X.X.X.X
     */
    private String getFormatedAddress() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        final String formatedIpAddress = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));

        return formatedIpAddress;
    }

}
