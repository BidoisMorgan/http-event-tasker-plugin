package bidoismorgan.httpevent.ui;

import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

import bidoismorgan.httpevent.Constants;
import bidoismorgan.httpevent.R;
import bidoismorgan.httpevent.bundle.PluginBundleManager;

/**
 * Created by Bidois Morgan on 01/04/15.
 */
public class EditActivity extends AbstractPluginActivity {

    private static int PORT = 8765;
    private EditText editName;
    private ListFilterAdapter listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_activity);

        TextView txtAddr = (TextView) findViewById(R.id.txt_addr);
        txtAddr.setText(getFormatedAddress());

        TextView txtPort = (TextView) findViewById(R.id.txt_port);
        txtPort.setText(Integer.toString(PORT));

        listAdapter = new ListFilterAdapter(this);
        ListView listFilterView = (ListView) findViewById(R.id.list_view_filter);
        listFilterView.setAdapter(listAdapter);
        listFilterView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        Button btnAdd = (Button) findViewById(R.id.btn_new_filter);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listAdapter.add("");
            }
        });

        editName = (EditText) findViewById(R.id.edit_txt_name);
    }

    @Override
    public void finish() {
        final Intent resultIntent = new Intent();

        String eventName = "Default";

        if (!editName.getText().equals("")) {
            eventName = String.valueOf(editName.getText());
        }

        /*
         * This extra is the data to ourselves: either for the Activity or the BroadcastReceiver. Note
         * that anything placed in this Bundle must be available to Locale's class loader. So storing
         * String, int, and other standard objects will work just fine. Parcelable objects are not
         * acceptable, unless they also implement Serializable. Serializable objects must be standard
         * Android platform objects (A Serializable class private to this plug-in's APK cannot be
         * stored in the Bundle, as Locale's classloader will not recognize it).
         */
        final Bundle resultBundle = PluginBundleManager.generateBundle(getApplicationContext(), true);
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
