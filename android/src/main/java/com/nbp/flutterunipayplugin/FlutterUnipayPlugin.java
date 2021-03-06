package com.nbp.flutterunipayplugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;

import com.unionpay.UPPayAssistEx;

import io.flutter.plugin.common.BasicMessageChannel;
import io.flutter.plugin.common.JSONUtil;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.common.StringCodec;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * FlutterUnipayPlugin
 */
public class FlutterUnipayPlugin implements MethodCallHandler, PluginRegistry.ActivityResultListener {

    //***************************************************************************************//
    // Plugin Interface Contract for all clients
    //
    //***************************************************************************************//
    public static final String CHANNEL_NAME = "com.nbp.flutter_unipay_plugin";
    public static final String MSG_CHANNEL_NAME = "com.nbp.msg.flutter_unipay_plugin";
    public static final String UP_PAY_SUCCESS = "0000";
    public static final String UP_PAY_FAIL = "9999";
    public static final String UP_PAY_EXCEPTION = "8888";
    public static final String UP_PAY_DONE = "6666";
    public static final String UP_PAY_CANCEL = "7777";



    private static final String LOG_TAG = "FlutterUnipayPlugin";
    private static final int PLUGIN_VALID = 0;
    private static final int PLUGIN_NOT_INSTALLED = -1;
    private static final int PLUGIN_NEED_UPGRADE = 2;

    private Activity activity;
    static MethodChannel channel;
    static BasicMessageChannel<String> stringMsgChannel;

    /*****************************************************************
     * mMode??????????????? "00" - ???????????????????????? "01" - ????????????????????????
     *****************************************************************/
    private String mMode = "01";

    private FlutterUnipayPlugin(Activity activity) {
        this.activity = activity;
    }

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        channel = new MethodChannel(registrar.messenger(), CHANNEL_NAME);
        stringMsgChannel = new BasicMessageChannel<>(
                registrar.view(), MSG_CHANNEL_NAME, StringCodec.INSTANCE);
        final FlutterUnipayPlugin instance = new FlutterUnipayPlugin(registrar.activity());
        registrar.addActivityResultListener(instance);
        channel.setMethodCallHandler(instance);
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {

        switch (call.method) {
            case "getPlatformVersion":
                result.success("Android " + android.os.Build.VERSION.RELEASE);
                break;
            case "upPay":
                unPay(call, result);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private void unPay(MethodCall call, MethodChannel.Result result) {
        // With given arguments in call, start up Unipay prepare
        final String tn = call.argument("up_pay_tn");
        // mMode???????????????
        // 0 - ????????????????????????
        // 1 - ????????????????????????
        final String argMode = call.argument("up_pay_mode");
        if (argMode != null) this.mMode = argMode;
        int ret = UPPayAssistEx.startPay(this.activity, null, null, tn, this.mMode);
        if (ret == PLUGIN_NEED_UPGRADE || ret == PLUGIN_NOT_INSTALLED) {
            // ????????????????????????
            Log.e(LOG_TAG, " plugin not found or need upgrade!!!");

            AlertDialog.Builder builder = new AlertDialog.Builder(this.activity);
            builder.setTitle("??????");
            builder.setMessage("????????????????????????????????????????????????????????????");

            final Context ctx = this.activity;

            builder.setNegativeButton("??????",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            UPPayAssistEx.installUPPayPlugin(ctx);
                            dialog.dismiss();
                        }
                    });

            builder.setPositiveButton("??????",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            builder.create().show();

        }
    }

    @Override
    public boolean onActivityResult(int i, int i1, Intent data) {
        /*************************************************
         * ??????3??????????????????????????????????????????????????????
         ************************************************/
        if (data == null) {
            return true;
        }

        Map<String, String> payload = new HashMap<>();
        /*
         * ???????????????????????????:success???fail???cancel ??????????????????????????????????????????????????????
         */
        String str = data.getExtras().getString("pay_result");
        if (str.equalsIgnoreCase("success")) {

            // ???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            // result_data?????????c???result_data????????????
            if (data.hasExtra("result_data")) {
                String result = data.getExtras().getString("result_data");
                try {
                    JSONObject resultJson = new JSONObject(result);
                    String sign = resultJson.getString("sign");
                    String dataOrg = resultJson.getString("data");
                    // ?????????verify?????????????????????????????????
                    // ????????????????????????????????????????????????????????????
                    boolean ret = verify(dataOrg, sign, this.mMode);
                    if (ret) {
                        // ?????????????????????????????????
                        payload.put("code", UP_PAY_SUCCESS);
                        payload.put("data", dataOrg );
                        payload.put("sign", sign);
                    } else {
                        // ????????????
                        payload.put("code", UP_PAY_FAIL);
                    }
                } catch (JSONException e) {
                    payload.put("code", UP_PAY_EXCEPTION);
                }
            } else {
                // TODO: ??????result_data?????????????????????????????????????????????????????????
                payload.put("code", UP_PAY_DONE);
            }
        } else if (str.equalsIgnoreCase("fail")) {
            payload.put("code", UP_PAY_FAIL);
        } else if (str.equalsIgnoreCase("cancel")) {
            payload.put("code", UP_PAY_CANCEL);
        }
        final JSONObject payloadJson = new JSONObject(payload);
        stringMsgChannel.send(payloadJson.toString(), new BasicMessageChannel.Reply<String>() {
            @Override
            public void reply(String s) {
                Log.i(LOG_TAG, "??????Dart?????????" + String.valueOf(s));
            }
        });

        return true;
    }

    private boolean verify(String msg, String sign64, String mode) {
        // TODO: Send message signature to backend and verify it
        return true;
    }
}
