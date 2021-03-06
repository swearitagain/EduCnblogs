package com.umeng;

import android.annotation.SuppressLint;
import android.app.Activity;
import java.util.List;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.pureman.MainActivity;
import com.umeng.message.IUmengCallback;
import com.umeng.message.MsgConstant;
import com.umeng.message.PushAgent;
import com.umeng.message.UTrack;
import com.umeng.message.UmengNotificationClickHandler;
import com.umeng.message.common.UmengMessageDeviceConfig;
import com.umeng.message.common.inter.ITagManager;
import com.umeng.message.entity.UMessage;
import com.umeng.message.tag.TagManager;

/**
 * Created by wangfei on 17/8/30
 */

public class PushModule extends ReactContextBaseJavaModule {
    private final int SUCCESS = 200;
    private final int ERROR = 0;
    private final int CANCEL = -1;
    private static final String TAG = PushModule.class.getSimpleName();
    private static Handler mSDKHandler = new Handler(Looper.getMainLooper());
    private static ReactApplicationContext context;
    private boolean isGameInited = false;
    private static Activity ma;
    private PushAgent mPushAgent;
    private Handler handler;
    // private OpenNativeModule openNativeModule = new OpenNativeModule(context);
    public static class NotificationReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO: This method is called when the BroadcastReceiver is receiving
            // an Intent broadcast.
            if(intent.getAction().equals("puremanNotification")){
                String params = intent.getExtras().getString("params");
                if(params != null){
                    sendEventToRn("notification",params);
                }
            }
        }
    }
    public PushModule(ReactApplicationContext reactContext) {
        super(reactContext);
        context = reactContext;
        mPushAgent = PushAgent.getInstance(context);
    }
    @Override
    public String getName() {
        return "UMPushModule";
    }

    @ReactMethod
    public void initClickHandler(Callback callback){
        UmengNotificationClickHandler notificationClickHandler = new UmengNotificationClickHandler() {

            public boolean isApplicationInBackground(Context context) {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningTaskInfo> taskList = am.getRunningTasks(1);
                if (taskList != null && !taskList.isEmpty()) {
                    ComponentName topActivity = taskList.get(0).topActivity;
                    if (topActivity != null && !topActivity.getPackageName().equals(context.getPackageName())) {
                        return true;
                    }
                }
                return false;
            }
            private boolean _isApplicationRunning(Context context) {
                ActivityManager activityManager = (ActivityManager) context.getApplicationContext().getSystemService(context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningAppProcessInfo> processInfos = activityManager.getRunningAppProcesses();
                for (ActivityManager.RunningAppProcessInfo processInfo : processInfos) {
                    if (processInfo.processName.equals(context.getApplicationContext().getPackageName())) {
                        if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                            for (String d: processInfo.pkgList) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
            @SuppressLint("WrongConstant")
            @Override
            public void dealWithCustomAction(Context context, UMessage msg) {

                Log.i("my_dealWithCustomAction","dealWithCustomAction执行");
                boolean backgroundFlag = isApplicationInBackground(context);
                //应用是否运行
                if(!_isApplicationRunning(context)){
                    Intent in = new Intent(context, MainActivity.class);
                    in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    context.startActivity(in);
                    if(backgroundFlag){ 
                        Intent intent =new Intent();
                        intent.setAction("puremanNotification");
                        intent.addFlags(0x01000000);
                        intent.putExtra("params",msg.getRaw().toString());
                        Activity currentActivity = MainActivity.getCurrentActivity();
                        currentActivity.sendBroadcast(intent);
                    }
                }
                else {
//                    Toast.makeText(context, "测试跳转", Toast.LENGTH_LONG).show();
                    Intent intent =new Intent();
                    intent.setAction("puremanNotification");
                    intent.addFlags(0x01000000);
                    intent.putExtra("params",msg.getRaw().toString());
                    Activity currentActivity = MainActivity.getCurrentActivity();
                    currentActivity.sendBroadcast(intent);
                }
//            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
//                    .emit("notification", params);
            }
            @Override
            public void openActivity(Context context, UMessage msg) {
                Log.i("my_openActivity","openActivity执行");
                //super.openActivity(context, msg);//不可调用，否则无效
//            Toast.makeText(context, "测试跳转", Toast.LENGTH_LONG).show();
                if(!_isApplicationRunning(context)){
//                Intent in = new Intent(context, MainActivity.class);
//                in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                context.startActivity(in);
                }
            }
        };
        //使用自定义的NotificationHandler，来结合友盟统计处理消息通知，参考http://bbs.umeng.com/thread-11112-1-1.html
        //CustomNotificationHandler notificationClickHandler = new CustomNotificationHandler();
        mPushAgent.setNotificationClickHandler(notificationClickHandler);
        callback.invoke(true);
    }

    public static void sendEventToRn(String eventName, @Nullable String params){
        context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("notification", params);
    }
    public static void initPushSDK(Activity activity) {
        ma = activity;
    }


    private static void runOnMainThread(Runnable runnable) {
        mSDKHandler.postDelayed(runnable, 0);
    }
    @ReactMethod
    public void getDeviceToken(Callback callback) {
        String registrationId = mPushAgent.getRegistrationId();
        callback.invoke(registrationId);
    }


    @ReactMethod
    public void disablePush(final Callback callback){
        mPushAgent.disable(new IUmengCallback() {
            @Override
            public void onSuccess() {
                callback.invoke(true);
            }

            @Override
            public void onFailure(String s, String s1) {
                callback.invoke(false);
            }

        });
    }

    @ReactMethod
    public void enablePush(final Callback callback){
        mPushAgent.enable(new IUmengCallback() {

            @Override
            public void onSuccess() {
                callback.invoke(true);
            }

            @Override
            public void onFailure(String s, String s1) {
                callback.invoke(false);
            }

        });
    }
    @ReactMethod
    public void addTag(String tag, final Callback successCallback) {
        mPushAgent.getTagManager().addTags(new TagManager.TCallBack() {
            @Override
            public void onMessage(final boolean isSuccess, final ITagManager.Result result) {


                        if (isSuccess) {
                            successCallback.invoke(SUCCESS,result.remain);
                        } else {
                            successCallback.invoke(ERROR,0);
                        }





            }
        }, tag);
    }

    @ReactMethod
    public void deleteTag(String tag, final Callback successCallback) {
        mPushAgent.getTagManager().deleteTags(new TagManager.TCallBack() {
            @Override
            public void onMessage(boolean isSuccess, final ITagManager.Result result) {
                Log.i(TAG, "isSuccess:" + isSuccess);
                if (isSuccess) {
                    successCallback.invoke(SUCCESS,result.remain);
                } else {
                    successCallback.invoke(ERROR,0);
                }
            }
        }, tag);
    }

    @ReactMethod
    public void listTag(final Callback successCallback) {
        mPushAgent.getTagManager().getTags(new TagManager.TagListCallBack() {
            @Override
            public void onMessage(final boolean isSuccess, final List<String> result) {
                mSDKHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isSuccess) {
                            if (result != null) {

                                successCallback.invoke(SUCCESS,resultToList(result));
                            } else {
                                successCallback.invoke(ERROR,resultToList(result));
                            }
                        } else {
                            successCallback.invoke(ERROR,resultToList(result));
                        }

                    }
                });

            }
        });
    }

    @ReactMethod
    public void addAlias(String alias, String aliasType, final Callback successCallback) {
        mPushAgent.addAlias(alias, aliasType, new UTrack.ICallBack() {
            @Override
            public void onMessage(final boolean isSuccess, final String message) {
                Log.i(TAG, "isSuccess:" + isSuccess + "," + message);

                        Log.e("xxxxxx","isuccess"+isSuccess);
                        if (isSuccess) {
                            successCallback.invoke(SUCCESS);
                        } else {
                            successCallback.invoke(ERROR);
                        }


            }
        });
    }

    @ReactMethod
    public void addAliasType() {
        Toast.makeText(ma,"function will come soon",Toast.LENGTH_LONG);
    }

    @ReactMethod
    public void addExclusiveAlias(String exclusiveAlias, String aliasType, final Callback successCallback) {
        mPushAgent.setAlias(exclusiveAlias, aliasType, new UTrack.ICallBack() {
            @Override
            public void onMessage(final boolean isSuccess, final String message) {

                        Log.i(TAG, "isSuccess:" + isSuccess + "," + message);
                        if (Boolean.TRUE.equals(isSuccess)) {
                            successCallback.invoke(SUCCESS);
                        }else {
                            successCallback.invoke(ERROR);
                        }



            }
        });
    }

    @ReactMethod
    public void deleteAlias(String alias, String aliasType, final Callback successCallback) {
        mPushAgent.deleteAlias(alias, aliasType, new UTrack.ICallBack() {
            @Override
            public void onMessage(boolean isSuccess, String s) {
                if (Boolean.TRUE.equals(isSuccess)) {
                    successCallback.invoke(SUCCESS);
                }else {
                    successCallback.invoke(ERROR);
                }
            }
        });
    }

    @ReactMethod
    public void appInfo(final Callback successCallback) {
        String pkgName = context.getPackageName();
        String info = String.format("DeviceToken:%s\n" + "SdkVersion:%s\nAppVersionCode:%s\nAppVersionName:%s",
            mPushAgent.getRegistrationId(), MsgConstant.SDK_VERSION,
            UmengMessageDeviceConfig.getAppVersionCode(context), UmengMessageDeviceConfig.getAppVersionName(context));
        successCallback.invoke("应用包名:" + pkgName + "\n" + info);
    }
    private WritableMap resultToMap(ITagManager.Result result){
        WritableMap map = Arguments.createMap();
        if (result!=null){
            map.putString("status",result.status);
            map.putInt("remain",result.remain);
            map.putString("interval",result.interval+"");
            map.putString("errors",result.errors);
            map.putString("last_requestTime",result.last_requestTime+"");
            map.putString("jsonString",result.jsonString);
        }
        return map;
    }
    private WritableArray resultToList(List<String> result){
        WritableArray list = Arguments.createArray();
        if (result!=null){
            for (String key:result){
                list.pushString(key);
            }
        }
        Log.e("xxxxxx","list="+list);
        return list;
    }
}