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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;


import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import taskerplugin.httpevent.Constants;
import taskerplugin.httpevent.TaskerPlugin;
import taskerplugin.httpevent.bundle.BundleScrubber;
import taskerplugin.httpevent.bundle.PluginBundleManager;

/**
 * This is the "query" BroadcastReceiver for a Locale Plug-in condition.
 *
 * @see com.twofortyfouram.locale.Intent#ACTION_QUERY_CONDITION
 * @see com.twofortyfouram.locale.Intent#EXTRA_BUNDLE
 */
public final class QueryReceiver extends BroadcastReceiver {

    private final static String PREFIX_TASKER_VAR_TPE = "%tpe_";

    /**
     * @param context {@inheritDoc}.
     * @param intent  the incoming {@link com.twofortyfouram.locale.Intent#ACTION_QUERY_CONDITION} Intent. This
     *                should always contain the {@link com.twofortyfouram.locale.Intent#EXTRA_BUNDLE} that was
     *                saved by {@link taskerplugin.httpevent.ui.EditActivity} and later broadcast by Locale.
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
                    // Get the filters from Bundle
                    ArrayList<String> filters = bundle.getStringArrayList(PluginBundleManager.BUNDLE_EXTRA_STRINGS_FILTERS);

                    if (Constants.IS_LOGGABLE) {
                        String filterString = "";
                        for (String f : filters) {
                            filterString += f + "|";
                        }
                        Log.v(Constants.LOG_TAG, "Filtres : " + filterString); //$NON-NLS-1$
                    }


                    // Get the parameters included in request
                    String URLParams = (String) dataBundle.get(PluginBundleManager.BUNDLE_EXTRA_STRING_URL);
//                    // Get map of parameters
//                    HashMap<String, String> mapParams = getMapParams(URLParams);
//                    areFiltersOK = checkFilters(mapParams, filters);
//                    // Transform the param into taskers varibales
//                    if (TaskerPlugin.Condition.hostSupportsVariableReturn(intent.getExtras())) {
//                        TaskerPlugin.addVariableBundle(getResultExtras(true), getBundleVariables(mapParams));
//                    }


                    if (URLParams != null) {
                        try {
                            JSONObject paramJSON = new JSONObject(URLParams);

                            if (!filters.isEmpty()) {
                                areFiltersOK = checkFilters(paramJSON, filters);
                            }

                            // Transform the param into taskers varibales
                            if (TaskerPlugin.Condition.hostSupportsVariableReturn(intent.getExtras())) {
                                Bundle varsBundle = new Bundle();
                                getBundleVariables(paramJSON, varsBundle);
                                TaskerPlugin.addVariableBundle(getResultExtras(true), varsBundle);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
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
            Intent backgroundIntent = new Intent(context, BackgroundService.class);
            backgroundIntent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE, bundle);
            context.startService(backgroundIntent);
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

//            if (pairs.length > 1) {
            for (int i = 0; i < pairs.length; i++) {
                String key = pairs[i].split("=")[0];
                String value = pairs[i].split("=")[1];
                filtersMap.put(key, value);
//                }
            }
        }

        return filtersMap;
    }

    /**
     * Function to transform the parameters of the request into Tasker Variables
     *
     * @param params : Map of the parameters
     * @return Bundle with the Tasker Variables
     */
    private Bundle getBundleVariables(HashMap<String, String> params) {
        Bundle varsBundle = new Bundle();

        for (Map.Entry<String, String> p : params.entrySet()) {
            // Tasker variables format
            String newVar = PREFIX_TASKER_VAR_TPE + p.getKey().toLowerCase();

            // Check if correct tasker var
            if (TaskerPlugin.variableNameValid(newVar)) {
                if (Constants.IS_LOGGABLE) {
                    Log.v(Constants.LOG_TAG, "Variable -> " + newVar); //$NON-NLS-1$
                }
                varsBundle.putString(newVar, p.getValue());
            }
        }

        return varsBundle;
    }

    /**
     * Function to transform the parameters of the request into Tasker Variables
     *
     * @param paramsJSON : JSON of the parameters
     * @return Bundle with the Tasker Variables
     */
    private void getBundleVariables(JSONObject paramsJSON, Bundle varsBundle) {

        Iterator<String> keys = paramsJSON.keys();
        while (keys.hasNext()) {
            String key = keys.next();

            // Tasker variables format
            String newVar = PREFIX_TASKER_VAR_TPE + key.toLowerCase();

            // Check if correct tasker var
            if (TaskerPlugin.variableNameValid(newVar)) {
                if (Constants.IS_LOGGABLE) {
                    Log.v(Constants.LOG_TAG, "Variable -> " + newVar); //$NON-NLS-1$
                }


                if (key.equals("message")) {
                    JSONObject innerObject = new JSONObject();
                    try {
                        // If key message is JSON, recall this method on it
                        innerObject = new JSONObject((String) paramsJSON.get(key));
                        getBundleVariables(innerObject, varsBundle);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                try {
                    varsBundle.putString(newVar, paramsJSON.get(key).toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }

        }
    }

    /**
     * Check if all the filters are present in params KEYS
     * For the moment just look the presence in the keys, next add values @TODO
     */
    private boolean checkFilters(HashMap<String, String> mapParams, ArrayList<String> filters) {
        for (String filter : filters) {
            if (!mapParams.containsKey(filter)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if all the filters are present in params KEYS
     * For the moment just look the presence in the keys, next add values @TODO
     */
    private boolean checkFilters(JSONObject paramsJSON, ArrayList<String> filters) {
        for (int i = 0; i < filters.size(); i++) {
            Iterator<String> keys = paramsJSON.keys();
            boolean isFilterInParam = false;
            while (keys.hasNext()) {
                String key = keys.next();

                if (key.equals(filters.get(i))) {
                    isFilterInParam = true;
                } else if (key.equals("message")) {
                    JSONObject innerObject = new JSONObject();
                    try {
                        // If key message is JSON check on its own keys/value
                        innerObject = new JSONObject((String) paramsJSON.get(key));
                        Iterator<String> keysMsg = innerObject.keys();
                        while (keysMsg.hasNext()) {
                            String keyMsg = keysMsg.next();
                            if (keyMsg.equals(filters.get(i))) {
                                isFilterInParam = true;
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
            // If the current filter checked was not found, return false
            if (!isFilterInParam) {
                return false;
            }
        }
        return true;
    }


}
