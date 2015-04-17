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

package bidoismorgan.httpevent.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import bidoismorgan.httpevent.Constants;
import bidoismorgan.httpevent.TaskerPlugin;
import bidoismorgan.httpevent.bundle.BundleScrubber;
import bidoismorgan.httpevent.bundle.PluginBundleManager;

/**
 * This is the "query" BroadcastReceiver for a Locale Plug-in condition.
 *
 * @see com.twofortyfouram.locale.Intent#ACTION_QUERY_CONDITION
 * @see com.twofortyfouram.locale.Intent#EXTRA_BUNDLE
 */
public final class QueryReceiver extends BroadcastReceiver {

    /**
     * @param context {@inheritDoc}.
     * @param intent  the incoming {@link com.twofortyfouram.locale.Intent#ACTION_QUERY_CONDITION} Intent. This
     *                should always contain the {@link com.twofortyfouram.locale.Intent#EXTRA_BUNDLE} that was
     *                saved by {@link bidoismorgan.httpevent.ui.EditActivity} and later broadcast by Locale.
     */
    @Override
    public void onReceive(final Context context, final Intent intent) {
        /*
         * Always be strict on input parameters! A malicious third-party app could send a malformed Intent.
         */
        if (!com.twofortyfouram.locale.Intent.ACTION_QUERY_CONDITION.equals(intent.getAction())) {
            if (Constants.IS_LOGGABLE) {
                Log.e(Constants.LOG_TAG,
                        String.format(Locale.US, "Received unexpected Intent action %s", intent.getAction())); //$NON-NLS-1$
            }
            return;
        }

        BundleScrubber.scrub(intent);

        final Bundle bundle = intent.getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE);
        BundleScrubber.scrub(bundle);

        if (PluginBundleManager.isBundleValid(bundle)) {
            // Check if messageId in intent
            final int messageID = TaskerPlugin.Event.retrievePassThroughMessageID(intent);

            if (messageID == -1) {
                setResultCode(com.twofortyfouram.locale.Intent.RESULT_CONDITION_UNKNOWN);
            } else {

                if (Constants.IS_LOGGABLE) {
                    Log.v(Constants.LOG_TAG, "--> new message : " + messageID); //$NON-NLS-1$
                    Log.v(Constants.LOG_TAG, "Event name = " + intent.getStringExtra(com.twofortyfouram.locale.Intent.EXTRA_STRING_BLURB)); //$NON-NLS-1$
                }

                // Get the DataBundle from the intent
                Bundle dataBundle = TaskerPlugin.Event.retrievePassThroughData(intent);

                /*
                 *   Boolean to know if parameters included in request are present in the filters
                 *   By default at true, because if no dataBundle, there is no filters so the conditions are Ok
                 */
                boolean areFiltersOK = true;

                if (dataBundle != null) {
                    // Get the parameters included in request
                    String URLParams = (String) dataBundle.get(PluginBundleManager.BUNDLE_EXTRA_STRING_URL);

                    // Get the filters from Bundle
                    ArrayList<String> filters = bundle.getStringArrayList(PluginBundleManager.BUNDLE_EXTRA_STRINGS_FILTERS);

                    if (Constants.IS_LOGGABLE) {
                        String filterString = "";
                        for (String f : filters) {
                            filterString += f + "|";
                        }
                        Log.v(Constants.LOG_TAG, "Filtres : " + filterString); //$NON-NLS-1$
                    }

                    // Get map of parameters
                    HashMap<String, String> mapParams = getMapParams(URLParams);

                    /*
                     * Check if all the filters are present in params KEYS
                     * For the moment just look the presence in the keys, next add values @TODO
                     */
                    for (String filter : filters) {
                        if (!mapParams.containsKey(filter)) {
                            areFiltersOK = false;
                        }
                    }
                }

                if (Constants.IS_LOGGABLE) {
                    Log.v(Constants.LOG_TAG, "Condition satsified ? -> " + areFiltersOK); //$NON-NLS-1$
                }

                if (areFiltersOK) {
                    setResultCode(com.twofortyfouram.locale.Intent.RESULT_CONDITION_SATISFIED);
                } else {
                    setResultCode(com.twofortyfouram.locale.Intent.RESULT_CONDITION_UNSATISFIED);
                }
            }

            /*
             * Because conditions are queried in the background and possibly while the phone is asleep, it is
             * necessary to acquire a WakeLock in order to guarantee that the service is started.
             */
            ServiceWakeLockManager.aquireLock(context);

            /*
             * Launch the backgroundService managing the HTTP server
             */
            context.startService(new Intent(context, BackgroundService.class));
        }
    }

    /**
     * Function to converse the Url string into map of <key,value> filters
     *
     * @param URLRaw : Request parameters String of the filters. Format : key1=value1&key2=value2...
     * @return Map<String,String> of filters
     */
    private HashMap<String, String> getMapParams(String URLRaw) {
        HashMap<String, String> filtersMap = new HashMap<>();

        if (URLRaw != null) {
            String[] pairs = URLRaw.split("&");

            for (int i = 0; i < pairs.length; i++) {
                String key = pairs[i].split("=")[0];
                String value = pairs[i].split("=")[1];
                filtersMap.put(key, value);
            }
        }

        return filtersMap;
    }
}