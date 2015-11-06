package com.foxcubgames.fconeappplugin;

import android.os.Bundle;
import android.util.Log;

import com.cloudzilla.fb.FacebookServiceProxy;
import com.facebook.FacebookSdk;
import com.unity3d.player.UnityPlayerActivity;


public class FcOneAppActivity extends UnityPlayerActivity {
    private final static String TAG = "FoxCub";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FacebookSdk.sdkInitialize(getApplicationContext());

        FacebookServiceProxy.FacebookServiceListener listener = new FacebookServiceProxy.FacebookServiceListener() {
                @Override
                public void onConnected() {
                    Log.i(TAG, "FacebookServiceProxy.onConnected");
                }
                @Override
                public void onConnectionError() {
                    Log.e(TAG, "FacebookServiceProxy.onConnectionError");
                }
                @Override
                public void onDisconnected() {
                    Log.i(TAG, "FacebookServiceProxy.onDisconnected");
                }
            };

        boolean onCloudzilla = FacebookServiceProxy.createInstanceAndLoginIfOnFacebook(this, listener);
        Log.i(TAG, "onCloudzilla = " + onCloudzilla);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        FacebookServiceProxy.deleteInstance();
    }
}