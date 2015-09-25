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

package com.cloudzilla.fb;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import com.cloudzilla.fb.IFacebookService;
import com.facebook.AccessToken;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.internal.Utility;
import com.facebook.share.internal.LikeContent;
import com.facebook.share.internal.ShareConstants;
import com.facebook.share.model.*;
import com.facebook.share.model.ShareContent;
import com.facebook.share.widget.AppInviteDialog;
import com.facebook.share.widget.ShareDialog;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * This class provides a proxy to 1App's FacebookService. It has been
 * tested with Facebook SDK for Android version 3.8.0.
 */
public class FacebookServiceProxy {
    public static interface FacebookServiceListener {
        /**
         * Override to get notifications when connecting/disconnecting to
         * the FacebookService
         */
        void onConnected();
        void onConnectionError();
        void onDisconnected();
    }

    private static final String TAG = "FacebookServiceProxy";
    // On Android L/5.0 the package name must be explicitly set for
    // the intent, otherwise binding to the service will fail. See
    // https://code.google.com/p/android/issues/detail?id=78505.
    private static final Intent FACEBOOK_SERVICE_INTENT =
            new Intent("com.cloudzilla.fb.FacebookService.BIND").setPackage(
                    "com.cloudzilla.fb");
    private static final int FACEBOOK_SERVICE_API_VERSION = 1;

    private Context mContext;
    private FacebookServiceListener mFacebookServiceListener;
    private IFacebookService mFacebookService = null;
    private FacebookServiceConnection mConnection = null;
    private static FacebookServiceProxy mInstance = null;

    /**
     * This helper method creates the proxy, connects to the
     * FacebookService, and authenticates to Facebook. All that if
     * the app is running on 1App; otherwise, the method returns
     * false. Call it from your main Activity's onCreate()
     * method. When the connection attempt to the FacebookService
     * completes, the method notifies the supplied listener, from
     * where you can check if the connection was established
     * successfully.
     *
     * @param context  the app context
     * @param listener the optional listener for connection events
     * @return true if the app is on 1App; false otherwise
     */
    public static boolean createInstanceAndLoginIfOnFacebook(
            Context context, final FacebookServiceListener listener) {
        boolean isOn1App = isOn1App(context);
        if (!isOn1App) {
            Log.i(TAG, "You are not on 1App");
            if (listener != null) {
                listener.onConnectionError();
            }
            return false;
        }

        Log.i(TAG, "You are on 1App");
        boolean success = createInstance(
                context,
                new FacebookServiceListener() {
                    @Override
                    public void onConnected() {
                        boolean result = getInstance().setFacebookAccessToken();
                        if (listener != null) {
                            if (result) {
                                listener.onConnected();
                            } else {
                                listener.onConnectionError();
                            }
                        }
                    }
                    @Override public void onConnectionError() {
                        Log.e(TAG, "Failed to connect to FacebookService");
                        if (listener != null) {
                            listener.onConnectionError();
                        }
                    }
                    @Override public void onDisconnected() {
                        if (listener != null) {
                            listener.onDisconnected();
                        }
                    }
                });

        if (!success) {
            Log.e(TAG, "Failed to create instance");
            if (listener != null) {
                listener.onConnectionError();
            }
        }

        return success;
    }

    /**
     * Replacement method for {@link
     * com.facebook.share.widget.ShareDialog.show()} using the
     * provided Activity. No callback will be invoked.
     *
     * @param dialog ShareDialog to use to share the provided content
     * @param shareContent Content to share
     */
    public static void show(ShareDialog dialog,
                            ShareContent<?, ?> shareContent) {
        boolean useNative = true;
        if (mInstance != null && mInstance.isOnFacebook()) {
            useNative = !mInstance.showHelper(shareContent);
        }
        if (useNative) {
            dialog.show(shareContent);
        }
    }

    /**
     * Replacement method for {@link
     * com.facebook.share.widget.ShareDialog.show()} using the
     * provided Activity. No callback will be invoked.
     *
     * @param activity Activity to use to share the provided content
     * @param shareContent Content to share
     */
    public static void show(Activity activity,
                            ShareContent<?, ?> shareContent) {
        boolean useNative = true;
        if (mInstance != null && mInstance.isOnFacebook()) {
            useNative = !mInstance.showHelper(shareContent);
        }
        if (useNative) {
            new ShareDialog(activity).show(shareContent);
        }
    }

    /**
     * Replacement method for {@link
     * com.facebook.share.widget.ShareDialog.show()} using the
     * provided Fragment. No callback will be invoked.
     *
     * @param fragment Fragment to use to share the provided content
     * @param shareContent Content to share
     */
    public static void show(Fragment fragment,
                            ShareContent<?, ?> shareContent) {
        boolean useNative = true;
        if (mInstance != null && mInstance.isOnFacebook()) {
            useNative = !mInstance.showHelper(shareContent);
        }
        if (useNative) {
            new ShareDialog(fragment).show(shareContent);
        }
    }

    private void addCommonParams(ShareContent<?, ?> shareContent,
                                 Bundle params) {
        if (shareContent.getContentUrl() != null) {
            params.putString("link", Utility.getUriString(shareContent.getContentUrl()));
        }
        final List<String> peopleIds = shareContent.getPeopleIds();
        if (!Utility.isNullOrEmpty(peopleIds)) {
            params.putString("tags", TextUtils.join(", ", peopleIds));
        }
        if (!Utility.isNullOrEmpty(shareContent.getPlaceId())) {
            params.putString("place", shareContent.getPlaceId());
        }
        if (!Utility.isNullOrEmpty(shareContent.getRef())) {
            params.putString("ref", shareContent.getRef());
        }
    }

    private boolean showHelper(ShareContent<?, ?> shareContent
                               /*, FacebookCallback<com.facebook.share.Sharer.Result> shareCallback = null*/) {
        String action = null;
        Bundle params = new Bundle();

        addCommonParams(shareContent, params);
        
        if (shareContent instanceof ShareLinkContent) {
            action = "feed";
            ShareLinkContent linkContent = (ShareLinkContent) shareContent;
            params.putString("picture", Utility.getUriString(linkContent.getImageUrl()));
            params.putString("name", linkContent.getContentTitle());
            params.putString("description", linkContent.getContentDescription());

        } else if (shareContent instanceof SharePhotoContent) {
            // FIXME
            //            SharePhotoContent photoContent = (SharePhotoContent) shareContent;
            //            for (SharePhoto sharePhoto: photoContent.getPhotos) {
            //            }
            
        } else if (shareContent instanceof ShareVideoContent) {
            // FIXME
            
        } else if (shareContent instanceof ShareOpenGraphContent) {
            // FIXME
        }

        if (action == null) {
            Log.e(TAG, "Unexpected action");
            return false;
        }
        
        showFacebookDialog(action, params);
        return true;
    }

    /**
     * Replacement method for {@link
     * com.facebook.share.widget.AppInviteDialog.show()} using the
     * provided Activity. No callback will be invoked.
     *
     * @param dialog AppInviteDialog to use to share the provided content
     * @param shareContent Content to share
     */
    public static void show(AppInviteDialog dialog,
                            AppInviteContent inviteContent) {
        boolean useNative = true;
        if (mInstance != null && mInstance.isOnFacebook()) {
            useNative = !mInstance.showHelper(inviteContent);
        }
        if (useNative) {
            dialog.show(inviteContent);
        }
    }

    /**
     * Replacement method for {@link
     * com.facebook.share.widget.AppInviteContent.show()} using the
     * provided Activity. No callback will be invoked.
     *
     * @param activity Activity to use to share the provided content
     * @param shareContent Content to share
     */
    public static void show(Activity activity,
                            AppInviteContent inviteContent) {
        boolean useNative = true;
        if (mInstance != null && mInstance.isOnFacebook()) {
            useNative = !mInstance.showHelper(inviteContent);
        }
        if (useNative) {
            new AppInviteDialog(activity).show(inviteContent);
        }
    }

    /**
     * Replacement method for {@link
     * com.facebook.share.widget.AppInviteContent.show()} using the
     * provided Fragment. No callback will be invoked.
     *
     * @param fragment Fragment to use to share the provided content
     * @param shareContent Content to share
     */
    public static void show(Fragment fragment,
                            AppInviteContent inviteContent) {
        boolean useNative = true;
        if (mInstance != null && mInstance.isOnFacebook()) {
            useNative = !mInstance.showHelper(inviteContent);
        }
        if (useNative) {
            new AppInviteDialog(fragment).show(inviteContent);
        }
    }
    
    private boolean showHelper(AppInviteContent inviteContent) {
        String action = "apprequests";
        Bundle params = new Bundle();
        //        addCommonParams(inviteContent, params);
        params.putString(ShareConstants.APPLINK_URL, inviteContent.getApplinkUrl());
        params.putString(ShareConstants.PREVIEW_IMAGE_URL, inviteContent.getPreviewImageUrl());
        showFacebookDialog(action, params);
        return true;
    }

    /**
     * This function provides a 1App replacement for using
     * either com.facebook.Session.requestNewReadPermissions() or
     * com.facebook.Session.requestNewWritePermissions(), as in the
     * following snippet:
     * <pre>
     *   List&lt;String&gt; permissions = new ArrayList&lt;String&gt;();
     *   permissions.add("create_event");
     *   permissions.add("rsvp_event");
     *   FacebookServiceProxy.requestNewPermissions(permissions, null);
     * </pre>
     *
     * @param permissions the permissions to request
     * @param callback an optional callback for session status changes
     */
    public void requestNewPermissions(List<String> permissions) {
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
        requestNewPermissions(permissionsAsStr);
    }

    public void requestNewPermissions(String permissions) {
        try {
            // The formats of JSON object opts is
            // documented at
            // https://developers.facebook.com/docs/reference/javascript/FB.login.
            JSONObject opts = new JSONObject();
            opts.put("scope", permissions);
            opts.put("return_scopes", "true");
            String responseAsStr = getService().login(opts.toString());
            JSONObject response = new JSONObject(responseAsStr);
            Log.d(TAG, "FB.login() response: " + response);
            if (!setFacebookAccessToken()) {
                Log.e(TAG, "Failed to ask for FB permissions");
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to ask for FB permissions", e);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to ask for FB permissions", e);
        }
    }

    public void showFacebookDialog(final String action, final Bundle params) {
        Log.d(TAG, "showFacebookDialog action=" + action + " params=" + toString(params));

        if (mInstance == null || !mInstance.isOnFacebook()) {
            Log.e(TAG, "You are not on Facebook");
            return;
        }

        new AsyncTask<Void, Void, JSONObject>() {
            private final IFacebookService facebookService = mFacebookService;

            protected JSONObject doInBackground(Void... nada) {
                JSONObject result = null;

                try {
                    JSONObject jsonRequest = new JSONObject();
                    jsonRequest.put("method", action);
                    for (String key : params.keySet()) {
                        jsonRequest.put(key, params.get(key));
                    }
                    String resultAsStr = facebookService.ui(jsonRequest.toString());
                    if (resultAsStr != null) {
                        result = new JSONObject(resultAsStr);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Exception: ", e);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to invoke FacebookService", e);
                }

                return result;
            }

            protected void onPostExecute(JSONObject result) {
                Bundle bundle = null;
                if (result != null) {
                    try {
                        bundle = toBundle(result);
                    } catch (JSONException e) {
                        // Nothing to do. We'll return a Facebook
                        // exception below.
                    }

                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Response from Facebook: ");
                        for (String key: bundle.keySet()) {
                            Log.d(TAG, "\t" + key + "=" + bundle.get(key));
                        }
                    }
                    //                    listener.onComplete(bundle, null);
                } else {
                    //                    listener.onComplete(null, new FacebookException());
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private static String toString(Bundle bundle) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (String key: bundle.keySet()) {
            sb.append(key + ":" + bundle.get(key) + ",");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Create the proxy and connect to the FacebookService. Note that
     * you'll still need to call loginToFacebook() once the
     * connection with FacebookService is established, i.e., when
     * listener.onConnection() is called. Call this method from your
     * main Activity's onCreate() method.
     *
     * @param context  the app context
     * @param listener the optional listener for connection events
     * @return true if the instance was created; false if it already exists
     */
    private static boolean createInstance(Context context,
                                          FacebookServiceListener listener) {
        if (mInstance != null) {
            Log.e(TAG, "The instance was already created");
            return false;
        }

        mInstance = new FacebookServiceProxy(context);

        // Bind to 1App's FacebookService.
        boolean success = mInstance.connect(listener);
        if (!success) {
            Log.e(TAG, "Failed to connect to FacebookService");
            if (listener != null) {
                listener.onConnectionError();
            }
        }

        return success;
    }

    /**
     * This method deletes the Proxy and closes its connection to the
     * FacebookService. Call it from your main Activity's onDestroy()
     * method.
     */
    public static void deleteInstance() {
        if (mInstance != null) {
            mInstance.disconnect();
        }
        mInstance = null;
    }

    /**
     * This method provides access to the Proxy from anywhere in your
     * app. It must be called only after the Proxy has been
     * initialized with the methods above.
     *
     * @return the instance, or null if it has not been created yet
     */
    public static FacebookServiceProxy getInstance() {
        if (mInstance == null) {
            Log.e(TAG, "The instance has not been created yet");
        }
        return mInstance;
    }

    /**
     * Determine if this is running on the 1App platform.
     *
     * @param context the app context
     * @return true if running on the 1App platform
     */
    public static boolean isOn1App(Context context) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> list = pm.queryIntentServices(FACEBOOK_SERVICE_INTENT,
                                                        0);
        return list.size() > 0;
    }

    /**
     * Determine if this is running on the 1App platform as a Facebook
     * Canvas app. Call this method only after establishing a
     * connection to the FacebookService. Calling it sooner than that
     * may cause false positives.
     *
     * @return true if running on the 1App platform as a Facebook Canvas app
     */
    public boolean isOnFacebook() {
        try {
            return (mFacebookService != null) &&
                    mFacebookService.isOnFacebook(FACEBOOK_SERVICE_API_VERSION) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public IFacebookService getService() {
        return mFacebookService;
    }

    // /////////////////////////////////////////////////////////////////////////

    private boolean setFacebookAccessToken() {
        AccessToken accessToken = getAccessToken();
        if (accessToken == null) {
            Log.e(TAG, "Failed to get FB access token");
            return false;
        }
        Log.d(TAG, "Setting FB access token: " + accessToken);
        AccessToken.setCurrentAccessToken(accessToken);
        boolean result = AccessToken.getCurrentAccessToken().getToken().equals(
                accessToken.getToken());
        if (!result) {
            Log.e(TAG, "Failed to set FB access token: " + accessToken);
        }
        return result;
    }

    private com.facebook.AccessToken getAccessToken() {
        String accessTokenString = null;
        List<String> permissions = null;
        List<String> declinedPermissions = null;
        String applicationId = null;
        String userId = null;
        Date expirationTime = null;
        Date lastRefreshTime = null;
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
                userId = mFacebookService.getUserId();
                expirationTime = new Date(mFacebookService.getAccessTokenExpirationTimeInMs());
                lastRefreshTime = new Date(mFacebookService.getAccessTokenLastRefreshTimeInMs());
            } else {
                Log.e(TAG, "You are not on Facebook: " + resultCode);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to invoke FacebookService", e);
        }

        if (accessTokenString == null ||
            permissions == null ||
            applicationId == null ||
            userId == null) {
            Log.e(TAG, "Some session information is missing");
            return null;
        }

        com.facebook.AccessTokenSource accessTokenSource =
                com.facebook.AccessTokenSource.WEB_VIEW;
        com.facebook.AccessToken accessToken =
                new com.facebook.AccessToken(
                        accessTokenString,
                        applicationId,
                        userId,
                        permissions,
                        declinedPermissions,
                        accessTokenSource,
                        expirationTime,
                        lastRefreshTime);

        return accessToken;
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
        @SuppressWarnings("unchecked")
            Iterator<String> iter = json.keys();
        while (iter.hasNext()) {
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
                Date expirationTime = new Date(mFacebookService.getAccessTokenExpirationTimeInMs());
                Log.i(TAG, "Expiration Time: " + expirationTime);
                Date lastRefreshTime = new Date(mFacebookService.getAccessTokenLastRefreshTimeInMs());
                Log.i(TAG, "Last Refresh Time: " + lastRefreshTime);
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

    /**
     * Bind to the FacebookService.
     *
     * @return true on success.
     */
    private boolean connect(FacebookServiceListener facebookServiceListener) {
        mFacebookServiceListener = facebookServiceListener;
        mConnection = new FacebookServiceConnection();
        //        FACEBOOK_SERVICE_INTENT.setPackage("com.cloudzilla.fb");
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
}
