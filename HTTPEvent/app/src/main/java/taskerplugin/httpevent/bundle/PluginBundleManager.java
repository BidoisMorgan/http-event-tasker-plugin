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

package taskerplugin.httpevent.bundle;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

import taskerplugin.httpevent.Constants;


/**
 * Class for managing the {@link com.twofortyfouram.locale.Intent#EXTRA_BUNDLE} for this plug-in.
 */
public final class PluginBundleManager {
    public static final String BASE_EXTRA_BUNDLE = "com.taskerplugin.httpevent.extra.";

    /**
     * Type: {@code int}.
     * <p/>
     * versionCode of the plug-in that saved the Bundle.
     */
    /*
     * This extra is not strictly required, however it makes backward and forward compatibility significantly
     * easier. For example, suppose a bug is found in how some version of the plug-in stored its Bundle. By
     * having the version, the plug-in can better detect when such bugs occur.
     */
    public static final String BUNDLE_EXTRA_INT_VERSION_CODE = BASE_EXTRA_BUNDLE + "INT_VERSION_CODE"; //$NON-NLS-1$

    public static final String BUNDLE_EXTRA_STRINGS_FILTERS = BASE_EXTRA_BUNDLE + "STRINGS_FILTERS"; //$NON-NLS-1$

    public static final String BUNDLE_EXTRA_STRING_URL = BASE_EXTRA_BUNDLE + "STRING_URL";

    public static final String BUNDLE_EXTRA_STRING_SOC_SERV_ADDR = BASE_EXTRA_BUNDLE + "STRING_SOC_SERV_ADDR";
    public static final String BUNDLE_EXTRA_STRING_SOC_SERV_LOGIN = BASE_EXTRA_BUNDLE + "STRING_SOC_SERV_LOGIN";
    public static final String BUNDLE_EXTRA_STRING_SOC_SERV_PASS = BASE_EXTRA_BUNDLE + "STRING_SOC_SERV_PASS";

    public static final String BUNDLE_EXTRA_STRING_HTTP_SERV_ADDR = BASE_EXTRA_BUNDLE + "STRING_HTTP_SERV_ADDR";
    public static final String BUNDLE_EXTRA_STRING_HTTP_SERV_PORT = BASE_EXTRA_BUNDLE + "STRING_HTTP_SERV_PORT";

    /**
     * Method to verify the content of the bundle are correct.
     * <p/>
     * This method will not mutate {@code bundle}.
     *
     * @param bundle bundle to verify. May be null, which will always return false.
     * @return true if the Bundle is valid, false if the bundle is invalid.
     */
    public static boolean isBundleValid(final Bundle bundle) {
        if (null == bundle) {
            return false;
        }

        /*
         * Make sure the expected extras exist
         */
        if (!bundle.containsKey(BUNDLE_EXTRA_STRINGS_FILTERS)) {
            if (Constants.IS_LOGGABLE) {
                Log.e(Constants.LOG_TAG,
                        String.format("bundle must contain extra %s", BUNDLE_EXTRA_STRINGS_FILTERS)); //$NON-NLS-1$
            }
            return false;
        }
        if (!bundle.containsKey(BUNDLE_EXTRA_INT_VERSION_CODE)) {
            if (Constants.IS_LOGGABLE) {
                Log.e(Constants.LOG_TAG,
                        String.format("bundle must contain extra %s", BUNDLE_EXTRA_INT_VERSION_CODE)); //$NON-NLS-1$
            }
            return false;
        }
        if (!bundle.containsKey(BUNDLE_EXTRA_STRING_SOC_SERV_ADDR) || !bundle.containsKey(BUNDLE_EXTRA_STRING_SOC_SERV_LOGIN) || !bundle.containsKey(BUNDLE_EXTRA_STRING_SOC_SERV_PASS)) {
            if (Constants.IS_LOGGABLE) {
                Log.e(Constants.LOG_TAG,
                        String.format("bundle must contain extra %s, %s, %s", BUNDLE_EXTRA_STRING_SOC_SERV_ADDR, BUNDLE_EXTRA_STRING_SOC_SERV_LOGIN, BUNDLE_EXTRA_STRING_SOC_SERV_PASS)); //$NON-NLS-1$
            }
            return false;
        }
        if (!bundle.containsKey(BUNDLE_EXTRA_STRING_HTTP_SERV_ADDR) || !bundle.containsKey(BUNDLE_EXTRA_STRING_HTTP_SERV_PORT)) {
            if (Constants.IS_LOGGABLE) {
                Log.e(Constants.LOG_TAG,
                        String.format("bundle must contain extra %s, %s", BUNDLE_EXTRA_STRING_HTTP_SERV_ADDR, BUNDLE_EXTRA_STRING_HTTP_SERV_PORT)); //$NON-NLS-1$
            }
            return false;
        }

        /*
         * Make sure the correct number of extras exist. Run this test after checking for specific Bundle
         * extras above so that the error message is more useful. (E.g. the caller will see what extras are
         * missing, rather than just a message that there is the wrong number).
         */
        if (7 != bundle.keySet().size()) {
            if (Constants.IS_LOGGABLE) {
                Log.e(Constants.LOG_TAG,
                        String.format("bundle must contain 5 keys, but currently contains %d keys: %s", bundle.keySet().size(), bundle.keySet())); //$NON-NLS-1$
            }
            return false;
        }

        /*
         * Make sure the extra is the correct type
         */
        if (bundle.getInt(BUNDLE_EXTRA_INT_VERSION_CODE, 0) != bundle.getInt(BUNDLE_EXTRA_INT_VERSION_CODE, 1)) {
            if (Constants.IS_LOGGABLE) {
                Log.e(Constants.LOG_TAG,
                        String.format("bundle extra %s appears to be the wrong type.  It must be an int", BUNDLE_EXTRA_INT_VERSION_CODE)); //$NON-NLS-1$
            }

            return false;
        }

        return true;
    }

    /**
     * @param context Application context.
     * @param filters
     * @return A plug-in bundle.
     */
    public static Bundle generateBundle(final Context context, final ArrayList<String> filters, String ssAddr, String ssLogin, String ssPass, String httpAddr, String httpPort) {
        final Bundle result = new Bundle();
        result.putInt(BUNDLE_EXTRA_INT_VERSION_CODE, Constants.getVersionCode(context));
        result.putStringArrayList(BUNDLE_EXTRA_STRINGS_FILTERS, filters);
        result.putString(BUNDLE_EXTRA_STRING_SOC_SERV_ADDR, ssAddr);
        result.putString(BUNDLE_EXTRA_STRING_SOC_SERV_LOGIN, ssLogin);
        result.putString(BUNDLE_EXTRA_STRING_SOC_SERV_PASS, ssPass);
        result.putString(BUNDLE_EXTRA_STRING_HTTP_SERV_ADDR, httpAddr);
        result.putString(BUNDLE_EXTRA_STRING_HTTP_SERV_PORT, httpPort);

        return result;
    }

    public static Bundle generateURLBundle(final Context context, final String url) {
        final Bundle result = new Bundle();
        result.putInt(BUNDLE_EXTRA_INT_VERSION_CODE, Constants.getVersionCode(context));
        result.putString(BUNDLE_EXTRA_STRING_URL, url);

        return result;
    }

    /**
     * Private constructor prevents instantiation
     *
     * @throws UnsupportedOperationException because this class cannot be instantiated.
     */
    private PluginBundleManager() {
        throw new UnsupportedOperationException("This class is non-instantiable"); //$NON-NLS-1$
    }
}