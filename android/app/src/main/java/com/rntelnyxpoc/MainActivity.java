package com.rntelnyxpoc;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.facebook.react.ReactActivity;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.telnyx.webrtc.sdk.CredentialConfig;
import com.telnyx.webrtc.sdk.TelnyxClient;
import com.telnyx.webrtc.sdk.model.LogLevel;
import com.telnyx.webrtc.sdk.model.TxServerConfiguration;
import com.telnyx.webrtc.sdk.verto.receive.ReceivedMessageBody;
import com.telnyx.webrtc.sdk.verto.receive.SocketObserver;

import java.io.IOException;

public class MainActivity extends ReactActivity /*implements LifecycleOwner*/ {

  private TelnyxClient telnyxClient;
    private String fcmToken = null;


    /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  @Override
  protected String getMainComponentName() {
    return "rnTelnyxPOC";
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

//    lifecycleRegistry = new LifecycleRegistry(this);
//    lifecycleRegistry.markState(Lifecycle.State.CREATED);

//      FirebaseApp.initializeApp(this);
      initTelnyx(this);
//      getFCMToken();
  }
//
//  @NonNull
//  @Override
//  public Lifecycle getLifecycle() {
//    return lifecycleRegistry;
//  }

  private void initTelnyx(Context context){
    telnyxClient = new TelnyxClient(context);
//        telnyxClient.connect(null);
    telnyxClient.connect(new TxServerConfiguration(
            "rtcdev.telnyx.com",
            14938,
            "turn:turn.telnyx.com:3478?transport=tcp",
            "stun:stun.telnyx.com:3843"
    ));

    observeSocketResponses(getReactInstanceManager().getCurrentReactContext());
  }

  private void observeSocketResponses(ReactContext reactContext){
        telnyxClient.getSocketResponse().observe(this,new SocketObserver<ReceivedMessageBody>(){

            @Override
            public void onError(@Nullable String message) {
              Toast.makeText(getApplicationContext(),"onError: "+message,Toast.LENGTH_LONG).show();
                WritableMap map = Arguments.createMap();
                map.putBoolean("Loading",false);
              sendEvent(reactContext,"Loading",map);
            }

            @Override
            public void onLoading() {
              Toast.makeText(getApplicationContext(),"onLoading",Toast.LENGTH_LONG).show();
            }

            @Override
            public void onMessageReceived(@Nullable ReceivedMessageBody data) {
              Toast.makeText(getApplicationContext(),"onMessageReceived",Toast.LENGTH_LONG).show();

                WritableMap map = Arguments.createMap();
                map.putBoolean("Loading",false);
                sendEvent(reactContext,"Loading",map);
            }

            @Override
            public void onConnectionEstablished() {
              Toast.makeText(getApplicationContext(),"Connection established",Toast.LENGTH_LONG).show();
              doLogin();
            }
        });
  }

  private void doLogin(){
    CredentialConfig credentialConfig= new CredentialConfig(
            "admin12345",
            "admin@123456",
//            "Patrick",
            "000000000",
            "000000000",
            fcmToken,
            R.raw.incoming_call,
            R.raw.ringback_tone,
            LogLevel.ALL
    );
    telnyxClient.credentialLogin(credentialConfig);
  }

    private void getFCMToken() {
        final String[] token = {""};
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {
                if (!task.isSuccessful()) {
//                    Timber.d("Fetching FCM registration token failed")
                    fcmToken = null;
                }
                else if (task.isSuccessful()){
                    // Get new FCM registration token
                    try {
                        token[0] = task.getResult();
                    } catch (Exception e) {
//                        Timber.d(e)
                    }
//                    Timber.d("FCM TOKEN RECEIVED: $token")
                }
                fcmToken = token[0];
            }
        });
    }

    private void sendEvent(ReactContext reactContext,
                           String eventName,
                           @Nullable WritableMap params) {
//        reactContext
//                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
//                .emit(eventName, params);
    }
}
