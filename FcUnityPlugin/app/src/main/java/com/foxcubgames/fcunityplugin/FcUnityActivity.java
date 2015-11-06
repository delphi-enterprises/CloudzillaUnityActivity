package com.foxcubgames.fcunityplugin;

import android.os.Bundle;
import com.facebook.FacebookSdk;
import com.unity3d.player.UnityPlayerActivity;


public class FcUnityActivity extends UnityPlayerActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FacebookSdk.sdkInitialize(getApplicationContext());
    }

}