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

        Log.e(Constants.LOG_TAG,
                String.format("INTENT %s TYPE %s %s", intent.getAction(), intent.getType(), intent.toString())); //$NON-NLS-1$

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
                                areFiltersOK = checkFilters(paramJSON, buildOperationFilters(filters));
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
                    Log.v(Constants.LOG_TAG, "Condition satsified ? -> " + String.valueOf(areFiltersOK).toUpperCase()); //$NON-NLS-1$
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
//                    Log.v(Constants.LOG_TAG, "Variable -> " + newVar); //$NON-NLS-1$
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
            String newVar = PREFIX_TASKER_VAR_TPE + key.trim().toLowerCase();

            // Check if correct tasker var
            if (TaskerPlugin.variableNameValid(newVar)) {
//                if (Constants.IS_LOGGABLE) {
//                    Log.v(Constants.LOG_TAG, "Variable -> " + newVar); //$NON-NLS-1$
//                }


                if (key.equals("message")) {
                    JSONObject innerObject = new JSONObject();
                    try {
                        // If key message is JSON, recall this method on it
                        innerObject = new JSONObject((String) paramsJSON.get(key));
                        getBundleVariables(innerObject, varsBundle);
                    } catch (JSONException e) {
                        // Don't print it because it is not important and can appears a lot
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
     * Function to test if all filter are satisfied
     * @param paramsJSON JSONObject of request's parameters
     * @param filtersOperation ArrayList of Operation for the filters
     * @return true if all filter are satisfied
     */
    private boolean checkFilters(JSONObject paramsJSON, ArrayList<Operation> filtersOperation) {
        for (int i = 0; i < filtersOperation.size(); i++) {

            Iterator<String> keysParams = paramsJSON.keys();
            boolean isFilterOK = false;

            while (keysParams.hasNext()) {
                String keyParam = keysParams.next();

                // If key message, test if there its json object in it
                if (keyParam.equals("message")) {
                    JSONObject innerObject = new JSONObject();
                    try {
                        // If key message is JSON check on its own keys/value
                        innerObject = new JSONObject((String) paramsJSON.get(keyParam));

                        Iterator<String> keysMsg = innerObject.keys();
                        while (keysMsg.hasNext()) {
                            String keyMsg = keysMsg.next();
                            // ATTENTION bug possible try catch trop large ici???
                            if (testParameter(keyMsg, innerObject.getString(keyMsg), filtersOperation.get(i))) {
                                isFilterOK = true;
                            }
                        }
                    } catch (JSONException e) {
                        // The message do not have a JSON Object so consider it like ohters
                        try {
                            if (testParameter(keyParam, paramsJSON.getString(keyParam), filtersOperation.get(i))) {
                                isFilterOK = true;
                            }
                        } catch (JSONException e1) {
                            e1.printStackTrace();
                        }
                    }

                } else {
                    try {
                        if (testParameter(keyParam, paramsJSON.getString(keyParam), filtersOperation.get(i))) {
                            isFilterOK = true;
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }


            }
            // If the current filter checked was not found, return false
            if (!isFilterOK) {
                return false;
            }
        }
        return true;
    }

    /**
     * Function to test if a parameter satisfy one operation
     *
     * @param paramKey   parameter key String
     * @param paramValue parameter value String
     * @param filterOp   operation filter to test
     * @return true if the parameter satisfy operation filter
     */

    private boolean testParameter(String paramKey, String paramValue, Operation filterOp) {
        // Test if the filter is just about presence of parameter or on value
        if (filterOp.isPresenceFilter()) {
            // Just presence -> test key and parameter of the opertion
            if (paramKey.trim().equals(filterOp.getParameter().trim())) {
                return true;
            }
        } else {
            try {
                // If the value of the parameter satisfy operation, filter Ok
                if (filterOp.makeOperation(paramKey, paramValue)) {
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Method to transform the list of filters into Operation object
     *
     * @param filters
     * @return
     */
    private ArrayList<Operation> buildOperationFilters(ArrayList<String> filters) {
        ArrayList<Operation> alOperations = new ArrayList<>();
        for (String filterString : filters) {
            Operation newOp;

            if (Constants.IS_LOGGABLE) {
                Log.v(Constants.LOG_TAG, "String to transform ? -> " + filterString); //$NON-NLS-1$
            }

            String[] elementsFilters = filterString.split("(?<===)|(?===)|(?=\\!=)|(?<=\\!=)");
            if (elementsFilters.length == 1) {
                newOp = new Operation(elementsFilters[0]);
            } else if (elementsFilters.length == 3) {
                newOp = new Operation(elementsFilters[0], elementsFilters[1], elementsFilters[2]);
            } else {
                // @todo à vérifier quoi faire ici..
                newOp = new Operation("");
            }
            alOperations.add(newOp);
        }
        return alOperations;
    }

    /**
     * Class representing an operation with a parameter, an operator and a value. Used to represent the filter operation
     * Operation -> parameter operator value -> param==value
     */
    private class Operation {

        private String parameter;
        private String value;
        private String operator;

        /**
         * It is possible to have filter without test on value. For this filter, the only test will be on the presence or not in the parameters.
         * This boolean is set to true for these particular filters
         */
        private boolean presenceFilter;

        public Operation(String param, String op, String value) {
            this.parameter = param;
            this.operator = op;
            this.value = value;
            this.presenceFilter = false;
        }

        public Operation(String param) {
            this.parameter = param;
            this.presenceFilter = true;
        }

        public String getParameter() {
            return this.parameter;
        }

        public boolean isPresenceFilter() {
            return this.presenceFilter;
        }

        /**
         * Function to make the test of the filter condition
         *
         * @param parameterValue Value of the parameter to test
         * @return true if the parameterValue have the same value or not, depending the operator
         * @throws Exception
         */
        public boolean makeOperation(String parameterKey, String parameterValue) throws Exception {
//            if (Constants.IS_LOGGABLE) {
//                Log.v(Constants.LOG_TAG, String.format("op key : %s op : %s op value : %s -- To test key : %s value : %s", parameter.trim(), operator.trim(), value.trim(), parameterKey.trim(), parameterValue.trim())); //$NON-NLS-1$
//            }
            // Test key
            if (this.parameter.trim().equals(parameterKey.trim())) {
                // Test value
                if (this.operator.equals("==")) {
                    return this.value.trim().equals(parameterValue.trim());
                } else if (this.operator.equals("!=")) {
                    return !this.value.trim().equals(parameterValue.trim());
                } else {
                    throw new Exception("Operator doesn't supported");
                }
            } else {
                return false;
            }
        }

    }


}
