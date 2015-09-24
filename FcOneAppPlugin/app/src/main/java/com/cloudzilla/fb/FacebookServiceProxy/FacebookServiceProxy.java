/* Copyright (c) 2013 Cloudzilla Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of
 * Cloudzilla Inc. ("Confidential Information").  You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Cloudzilla Inc.
 *
 * CLOUDZILLA INC. MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE
 * SUITABILITY OF THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE, OR NON-INFRINGEMENT. CLOUDZILLA
 * INC. WILL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A
 * RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
 * DERIVATIVES.
 */

package com.cloudzilla.fb.FacebookServiceProxy;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import com.cloudzilla.fb.IFacebookService;
import com.facebook.FacebookException;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.widget.WebDialog.OnCompleteListener;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * This class provides a proxy to Cloudzilla's FacebookService.
 */
public class FacebookServiceProxy {
    public static interface FacebookServiceListener {
        /** Override to get notifications when connecting/disconnecting to
         * the FacebookService **/
        void onConnected();
        void onConnectionError();
        void onDisconnected();
    }

    private static final String TAG = "FacebookServiceProxy";
    private static final Intent FACEBOOK_SERVICE_INTENT =
            new Intent("com.cloudzilla.fb.FacebookService.BIND");
    private static final int FACEBOOK_SERVICE_API_VERSION = 1;

    private Context mContext;
    private FacebookServiceListener mFacebookServiceListener;
    private IFacebookService mFacebookService = null;
    private FacebookServiceConnection mConnection = null;
    private static FacebookServiceProxy mInstance = null;

    /** This helper method creates the proxy, connects to the
     *  FacebookService, and authenticates to Facebook. All that if
     *  the app is running on Cloudzilla.  The method returns true on
     *  success. Call it from your main Activity's onCreate()
     *  method. **/
    public static boolean createInstanceAndLoginIfOnFacebook(Context context) {
        boolean success = false;
        boolean isOnCloudzilla = isOnCloudzilla(context);
        if (isOnCloudzilla) {
            Log.i(TAG, "You are on Cloudzilla");
            success = createInstance(
                    context,
                    new FacebookServiceListener() {
                        @Override
                        public void onConnected() {
                            getInstance().loginToFacebook(
                                    new com.facebook.Session.StatusCallback() {
                                        @Override
                                        public void call(Session session,
                                                         SessionState state,
                                                         Exception exception) {
                                            Log.i(TAG, "Facebook session state change: " +
                                                  state + " exception=" + exception);
                                        }
                                    });
                        }
                        @Override public void onConnectionError() {}
                        @Override public void onDisconnected() {}
                    });

        } else {
            Log.i(TAG, "You are not on Cloudzilla");
        }

        return success;
    }

    /** Create the proxy and connect to the FacebookService. Note that
     *  you'll still need to call loginToFacebook() once the
     *  connection with FacebookService is established, i.e., when
     *  listener.onConnection() is called. Call this method from your
     *  main Activity's onCreate() method. **/
    public static boolean createInstance(Context context,
                                         FacebookServiceListener listener) {
        if (mInstance != null) {
            Log.e(TAG, "The instance was already created");
            return false;
        }

        mInstance = new FacebookServiceProxy(context);

        // Bind to Cloudzilla's FacebookService.
        boolean success = mInstance.connect(listener);
        if (!success) {
            Log.e(TAG, "Failed to connect to FacebookService");
            if (listener != null) {
                listener.onConnectionError();
            }
        }

        return success;
    }

    /** This method deletes the Proxy and closes its connection to the
     * FacebookService. Call it from your main Activity's onDestroy()
     * method. **/
    public static void deleteInstance() {
        if (mInstance != null) {
            mInstance.disconnect();
        }
        mInstance = null;
    }

    /** This method provides access to the Proxy from anywhere in your
     * app. It must be called only after the Proxy has been
     * initialized with the methods above. **/
    public static FacebookServiceProxy getInstance() {
        if (mInstance == null) {
            Log.e(TAG, "The instance has not been created yet");
        }
        return mInstance;
    }

    /** Return true if running on the Cloudzilla platform. **/
    public static boolean isOnCloudzilla(Context context) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> list = pm.queryIntentServices(FACEBOOK_SERVICE_INTENT,
                                                        0);
        return list.size() > 0;
    }

    public IFacebookService getService() {
        return mFacebookService;
    }

    // /////////////////////////////////////////////////////////////////////////

    /** This function provides a Cloudzilla replacement for logging in
     * to Facebook. **/
    public void loginToFacebook(Session.StatusCallback callback) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Logging in to Facebook...");
        }

        Session activeSession = Session.getActiveSession();
        if (activeSession != null && activeSession.isOpened()) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Session already active");
            }
            return;
        }

        openSession(callback);
    }

    private void openSession(Session.StatusCallback callback) {
        Session activeSession = Session.getActiveSession();
        String accessTokenString = null;
        List<String> permissions = null;
        String applicationId = null;
        try {
            int resultCode = mFacebookService.isOnFacebook(
                    FACEBOOK_SERVICE_API_VERSION);
            if (resultCode == 0) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "You are on Facebook");
                }
                accessTokenString = mFacebookService.getAccessToken();
                permissions = mFacebookService.getPermissions();
                applicationId = mFacebookService.getApplicationId();
            } else {
                Log.e(TAG, "You are not on Facebook: " + resultCode);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to invoke FacebookService", e);
        }

        if (accessTokenString == null ||
            permissions == null ||
            applicationId == null) {
            Log.e(TAG, "Some session information is missing");
            if (callback != null) {
                callback.call(activeSession, SessionState.CLOSED_LOGIN_FAILED,
                              new FacebookException());
            }
            return;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Opening Facebook session with Cloudzilla access token: " +
                  accessTokenString);
        }
        Date expirationTime = null;
        Date lastRefreshTime = null;
        com.facebook.AccessTokenSource accessTokenSource =
                com.facebook.AccessTokenSource.WEB_VIEW;
        com.facebook.AccessToken accessToken =
                com.facebook.AccessToken.createFromExistingAccessToken(
                        accessTokenString,
                        expirationTime,
                        lastRefreshTime,
                        accessTokenSource,
                        permissions);

        Session.Builder builder = new Session.Builder(mContext);
        builder.setApplicationId(applicationId);
        Session session = builder.build();
        Session.setActiveSession(session);
        session.open(accessToken, callback);
    }

    /** This function provides a Cloudzilla replacement for using
     * either com.facebook.Session.requestNewReadPermissions() or
     * com.facebook.Session.requestNewWritePermissions(), as in the
     * following snippet:
     *
     *   List<String> permissions = new ArrayList<String>();
     *   permissions.add("create_event");
     *   permissions.add("rsvp_event");
     *   FacebookServiceProxy.requestNewPermissions(permissions, null);
     *
     **/
    public void requestNewPermissions(List<String> permissions,
                                      Session.StatusCallback callback) {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (String permission : permissions) {
            if (!isFirst) {
                sb.append(",");
            }
            sb.append(permission);
            isFirst = false;
        }
        String permissionsAsStr = sb.toString();
        try {
            // The formats of JSON object opts is
            // documented at
            // https://developers.facebook.com/docs/reference/javascript/FB.login.
            JSONObject opts = new JSONObject();
            opts.put("scope", permissionsAsStr);
            opts.put("return_scopes", "true");
            String responseAsStr = getService().login(opts.toString());
            JSONObject response = new JSONObject(responseAsStr);
            Log.d(TAG, "FB.login() response: " + response);
            openSession(callback);

        } catch (JSONException e) {
            Log.e(TAG, "Failed to ask for FB permissions", e);
            if (callback != null) {
                Session activeSession = Session.getActiveSession();
                callback.call(activeSession, SessionState.CLOSED_LOGIN_FAILED,
                              new FacebookException());
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to ask for FB permissions", e);
            if (callback != null) {
                Session activeSession = Session.getActiveSession();
                callback.call(activeSession, SessionState.CLOSED_LOGIN_FAILED,
                              new FacebookException());
            }
        }
    }

    /** This class provides a Cloudzilla replacement for using
     * com.facebook.WebDialog. It is not created directly. Refer to
     * its inner classes. **/
    public static class WebDialog extends com.facebook.widget.WebDialog {
        private String mAction;
        private Bundle mParams;
        private OnCompleteListener mListener;

        private WebDialog(Context context, String url, int theme, String action,
                          Bundle params, OnCompleteListener listener) {
            super(context, url, theme);
            mAction = action;
            mParams = params;
            mListener = listener;
        }

        @Override
        public void show() {
            FacebookServiceProxy.getInstance().showFacebookDialog(mAction, mParams,
                                                                  mListener);
        }

        /** This class provides a Cloudzilla replacement for using
         * com.facebook.WebDialog.Builder() as in the following
         * snippet:
         *
         *   WebDialog dialog = new WebDialog.Builder(
         *       context, session, action, params).setCompleteListener(listener).build();
         *   dialog.show();
         *
         **/
        public static class Builder extends com.facebook.widget.WebDialog.Builder {
            private String mAction;
            public Builder(Context context, Session session, String action, Bundle bundle) {
                super(context, session, action, bundle);
                mAction = action;
            }
            @Override
            public com.facebook.widget.WebDialog build() {
                return getInstance().isOnFacebook() ?
                        new WebDialog(getContext(), null, getTheme(),
                                      mAction, getParameters(),
                                      getListener()) :
                        super.build();
            }
        }

        /** This class provides a Cloudzilla replacement for using
         * com.facebook.WebDialog.FeedDialogBuilder() as in the following
         * snippet:
         *
         *   WebDialog dialog = new WebDialog.FeedDialogBuilder(
         *       context, session, params).setCompleteListener(listener).build();
         *   dialog.show();
         *
         **/
        public static class FeedDialogBuilder
                extends com.facebook.widget.WebDialog.FeedDialogBuilder {
            public FeedDialogBuilder(Context context, Session session) {
                super(context, session);
            }
            public FeedDialogBuilder(Context context, Session session, Bundle bundle) {
                super(context, session, bundle);
            }
            @Override
            public com.facebook.widget.WebDialog build() {
                return getInstance().isOnFacebook() ?
                        new WebDialog(getContext(), null, getTheme(),
                                      "feed", getParameters(),
                                      getListener()) :
                        super.build();
            }
        }

        /** This class provides a Cloudzilla replacement for using
         * com.facebook.WebDialog.RequestDialogBuilder() as in the
         * following snippet:
         *
         *   WebDialog dialog = new WebDialog.RequestsDialogBuilder(
         *       context, session, params).setCompleteListener(listener).build();
         *   dialog.show();
         *
         **/
        public static class RequestsDialogBuilder
                extends com.facebook.widget.WebDialog.RequestsDialogBuilder {
            public RequestsDialogBuilder(Context context, Session session) {
                super(context, session);
            }
            public RequestsDialogBuilder(Context context, Session session, Bundle bundle) {
                super(context, session, bundle);
            }
            @Override
            public com.facebook.widget.WebDialog build() {
                return getInstance().isOnFacebook() ?
                        new WebDialog(getContext(), null, getTheme(),
                                      "apprequests", getParameters(),
                                      getListener()) :
                        super.build();
            }
        }
    }

    // /////////////////////////////////////////////////////////////////////////

    public void showOfferWall(Activity activity, String currencyId,
                              int offerwallId)
            throws RemoteException, SendIntentException {
        if (mFacebookService == null) {
            Log.e(TAG, "You are not connected to FacebookService");
            return;
        }

        PendingIntent pendingIntent = mFacebookService.getOfferWallIntent(
                currencyId, offerwallId);
        int requestCode = 0;
        Intent fillInIntent = new Intent();
        int flagsMask = 0;
        int flagsValues = 0;
        int extraFlags = 0;
        activity.startIntentSenderForResult(pendingIntent.getIntentSender(),
                                            requestCode,
                                            fillInIntent,
                                            flagsMask,
                                            flagsValues,
                                            extraFlags);
    }

    public void showAdvertisement(Activity activity) throws RemoteException,
            SendIntentException {
        if (mFacebookService == null) {
            Log.e(TAG, "You are not connected to FacebookService");
            return;
        }

        PendingIntent pendingIntent = mFacebookService.getAdvertisementIntent();
        int requestCode = 0;
        Intent fillInIntent = new Intent();
        int flagsMask = 0;
        int flagsValues = 0;
        int extraFlags = 0;
        activity.startIntentSenderForResult(pendingIntent.getIntentSender(),
                                            requestCode,
                                            fillInIntent,
                                            flagsMask,
                                            flagsValues,
                                            extraFlags);
    }

    // /////////////////////////////////////////////////////////////////////////

    private static String getResultCodeString(int resultCode) {
        String result;
        switch (resultCode) {
            case 0:
                result = "OK";
                break;
            case 1:
                result = "WRONG_VERSION";
                break;
            case 2:
                result = "NOT_ON_FACEBOOK";
                break;
            case 3:
                result = "ERROR";
                break;
            case 4:
                result = "USER_CANCELED";
                break;
            default:
                result = "UNKNOWN";
                break;
        }
        return result;
    }

    private static Bundle toBundle(JSONObject json) throws JSONException {
        Bundle values = new Bundle();
        for (Iterator<String> iter = json.keys(); iter.hasNext();) {
            String key = iter.next();
            try {
                String value = json.getString(key);
                values.putString(key, value);
            } catch (JSONException e) {
                Log.e(TAG, "Failed to parse field " + key +
                      " of JSON response " + json, e);
                throw e;
            }
        }
        return values;
    }

    private class FacebookServiceConnection implements ServiceConnection {
        // Called when the connection with the service is established.
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "FacebookService connected " + name);
            }
            mFacebookService = IFacebookService.Stub.asInterface(service);
            Log.i(TAG, "FacebookService connected");
            try {
                int resultCode = mFacebookService.isOnFacebook(
                        FACEBOOK_SERVICE_API_VERSION);
                Log.i(TAG, "On FB: " + getResultCodeString(resultCode));
                String userId = mFacebookService.getUserId();
                Log.i(TAG, "User ID: " + userId);
                String accessToken = mFacebookService.getAccessToken();
                Log.i(TAG, "Access Token: " + accessToken);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to invoke FacebookService", e);
            }
            if (mFacebookServiceListener != null) {
                mFacebookServiceListener.onConnected();
            }
        }

        // Called when the connection with the service disconnects unexpectedly
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.e(TAG, "FacebookService has unexpectedly disconnected");
            mFacebookService = null;
            if (mFacebookServiceListener != null) {
                mFacebookServiceListener.onDisconnected();
            }
        }
    };

    private FacebookServiceProxy(Context context) {
        mContext = context;
    }

    private boolean isOnFacebook() {
        try {
            return (mFacebookService != null) &&
                    mFacebookService.isOnFacebook(FACEBOOK_SERVICE_API_VERSION) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Bind to the FacebookService. Return true on success. **/
    private boolean connect(FacebookServiceListener facebookServiceListener) {
        mFacebookServiceListener = facebookServiceListener;
        mConnection = new FacebookServiceConnection();
        if (mContext.bindService(FACEBOOK_SERVICE_INTENT, mConnection,
                                 Context.BIND_AUTO_CREATE)) {
            Log.i(TAG, "Binded to FacebookService");
            return true;
        } else {
            Log.e(TAG, "Failed to bind to FacebookService");
            return false;
        }
    }

    private void disconnect() {
        if (mConnection != null) {
            Log.i(TAG, "Unbinding from FacebookService");
            mContext.unbindService(mConnection);
        }
        mConnection = null;
    }

    private void showFacebookDialog(String action, Bundle params,
                                    OnCompleteListener listener) {
        if (mFacebookService == null) {
            Log.e(TAG, "You are not connected to FacebookService");
            if (listener != null) {
                listener.onComplete(null, new FacebookException());
            }
            return;
        }

        JSONObject result = null;
        try {
            JSONObject jsonRequest = new JSONObject();
            jsonRequest.put("method", action);
            for (String key : params.keySet()) {
                jsonRequest.put(key, params.get(key));
            }
            String resultAsStr = mFacebookService.ui(jsonRequest.toString());
            if (resultAsStr != null) {
                result = new JSONObject(resultAsStr);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Exception: ", e);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to invoke FacebookService", e);
        }
        if (listener != null) {
            Bundle bundle = null;
            if (result != null) {
                try {
                    bundle = toBundle(result);
                } catch (JSONException e) {
                    // Nothing to do. We'll return a Facebook
                    // exception below.
                }
            }
            if (result != null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Response from Facebook: ");
                    for (String key: bundle.keySet()) {
                        Log.d(TAG, "\t" + key + "=" + bundle.get(key));
                    }
                }
                listener.onComplete(bundle, null);
            } else {
                listener.onComplete(null, new FacebookException());
            }
        }
    }
}
