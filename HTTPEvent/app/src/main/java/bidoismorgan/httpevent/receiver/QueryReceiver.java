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
import android.os.PowerManager;
import android.util.Log;


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

        Log.v(Constants.LOG_TAG, "event name = " + intent.getStringExtra(com.twofortyfouram.locale.Intent.EXTRA_STRING_BLURB)); //$NON-NLS-1$

        final int messageID = TaskerPlugin.Event.retrievePassThroughMessageID(intent);

        if (PluginBundleManager.isBundleValid(bundle)) {
            final boolean isScreenOn =
                    (((PowerManager) context.getSystemService(Context.POWER_SERVICE)).isScreenOn());
            final boolean conditionState = bundle.getBoolean(PluginBundleManager.BUNDLE_EXTRA_BOOLEAN_STATE);

//            if (Constants.IS_LOGGABLE) {
//                Log.v(Constants.LOG_TAG,
//                        String.format(Locale.US,
//                                "Screen state is %b and condition state is %b", isScreenOn, conditionState)); //$NON-NLS-1$
//            }

            if (messageID == -1)
                setResultCode(com.twofortyfouram.locale.Intent.RESULT_CONDITION_UNKNOWN);
            else {
                Log.v(Constants.LOG_TAG, "current msgID = " + messageID); //$NON-NLS-1$

                Bundle dataBundle = TaskerPlugin.Event.retrievePassThroughData(intent);

                String buf = (String) dataBundle.get(PluginBundleManager.BUNDLE_EXTRA_STRING_URL);

                String eventName = intent.getStringExtra(com.twofortyfouram.locale.Intent.EXTRA_STRING_BLURB);
                Log.v(Constants.LOG_TAG, "event name = " + eventName + " vs url = " + buf); //$NON-NLS-1$

                if (buf == null || !buf.equals(eventName)) {
                    setResultCode(com.twofortyfouram.locale.Intent.RESULT_CONDITION_UNSATISFIED);
                } else {
                    setResultCode(com.twofortyfouram.locale.Intent.RESULT_CONDITION_SATISFIED);
                }
            }

            /*
             * Because conditions are queried in the background and possibly while the phone is asleep, it is
             * necessary to acquire a WakeLock in order to guarantee that the service is started.
             */
            ServiceWakeLockManager.aquireLock(context);

            /*
             * To detect screen changes as they happen, a service must be running because the SCREEN_ON/OFF
             * Intents are REGISTERED_RECEIVER_ONLY.
             *
             * To avoid a gap in detecting screen on/off changes, the current state of the screen needs to be
             * sent to the service.
             */
            context.startService(new Intent(context, BackgroundService.class).putExtra(BackgroundService.EXTRA_BOOLEAN_WAS_SCREEN_ON,
                    isScreenOn));
        }
    }
}