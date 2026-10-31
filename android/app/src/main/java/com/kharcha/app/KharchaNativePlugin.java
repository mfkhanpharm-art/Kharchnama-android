package com.kharcha.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * Native features for Kharcha: reading bank SMS from the inbox, hearing new SMS live,
 * and fingerprint / face unlock. Message text stays on the phone unless the user
 * chooses to send it to Faygul for categorisation.
 */
@CapacitorPlugin(
    name = "KharchaNative",
    permissions = {
        @Permission(alias = "sms", strings = { Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS })
    }
)
public class KharchaNativePlugin extends Plugin {

    private BroadcastReceiver smsReceiver;

    @Override
    public void load() {
        registerSmsReceiver();
    }

    @Override
    protected void handleOnDestroy() {
        if (smsReceiver != null) {
            try { getContext().unregisterReceiver(smsReceiver); } catch (Exception ignored) {}
            smsReceiver = null;
        }
    }

    private boolean smsGranted() {
        return getPermissionState("sms") == PermissionState.GRANTED;
    }

    private void registerSmsReceiver() {
        if (smsReceiver != null || !smsGranted()) return;
        smsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
                if (parts == null || parts.length == 0) return;
                StringBuilder body = new StringBuilder();
                String address = null;
                long ts = System.currentTimeMillis();
                for (SmsMessage m : parts) {
                    if (m == null) continue;
                    body.append(m.getMessageBody());
                    address = m.getOriginatingAddress();
                    ts = m.getTimestampMillis();
                }
                JSObject o = new JSObject();
                o.put("id", "live_" + ts);
                o.put("address", address);
                o.put("body", body.toString());
                o.put("date", ts);
                notifyListeners("sms", o, true);
            }
        };
        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        if (Build.VERSION.SDK_INT >= 33) {
            getContext().registerReceiver(smsReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            getContext().registerReceiver(smsReceiver, filter);
        }
    }

    @PluginMethod
    public void smsStatus(PluginCall call) {
        JSObject r = new JSObject();
        r.put("granted", smsGranted());
        call.resolve(r);
    }

    @PluginMethod
    public void requestSms(PluginCall call) {
        if (smsGranted()) {
            registerSmsReceiver();
            JSObject r = new JSObject();
            r.put("granted", true);
            call.resolve(r);
            return;
        }
        requestPermissionForAlias("sms", call, "smsPermissionCallback");
    }

    @PermissionCallback
    private void smsPermissionCallback(PluginCall call) {
        boolean granted = smsGranted();
        if (granted) registerSmsReceiver();
        JSObject r = new JSObject();
        r.put("granted", granted);
        call.resolve(r);
    }

    @PluginMethod
    public void readSms(PluginCall call) {
        if (!smsGranted()) {
            call.reject("SMS permission not granted", "denied");
            return;
        }
        long since = call.getLong("since", 0L);
        int limit = call.getInt("limit", 400);
        JSArray out = new JSArray();
        Cursor c = null;
        try {
            c = getContext().getContentResolver().query(
                Uri.parse("content://sms/inbox"),
                new String[] { "_id", "address", "body", "date" },
                "date > ?",
                new String[] { String.valueOf(since) },
                "date DESC"
            );
            int n = 0;
            if (c != null) {
                while (c.moveToNext() && n < limit) {
                    JSObject o = new JSObject();
                    o.put("id", c.getString(0));
                    o.put("address", c.getString(1));
                    o.put("body", c.getString(2));
                    o.put("date", c.getLong(3));
                    out.put(o);
                    n++;
                }
            }
        } catch (Exception e) {
            call.reject("Couldn't read messages: " + e.getMessage());
            return;
        } finally {
            if (c != null) c.close();
        }
        JSObject r = new JSObject();
        r.put("messages", out);
        call.resolve(r);
    }

    @PluginMethod
    public void biometricAvailable(PluginCall call) {
        int s = BiometricManager.from(getContext()).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        JSObject r = new JSObject();
        r.put("available", s == BiometricManager.BIOMETRIC_SUCCESS);
        call.resolve(r);
    }

    @PluginMethod
    public void biometricAuth(PluginCall call) {
        final String reason = call.getString("reason", "Confirm it's you");
        getActivity().runOnUiThread(() -> {
            BiometricPrompt prompt = new BiometricPrompt(
                (FragmentActivity) getActivity(),
                ContextCompat.getMainExecutor(getContext()),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        JSObject o = new JSObject();
                        o.put("ok", true);
                        call.resolve(o);
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence msg) {
                        JSObject o = new JSObject();
                        o.put("ok", false);
                        o.put("error", String.valueOf(msg));
                        call.resolve(o);
                    }
                }
            );
            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Kharcha")
                .setSubtitle(reason)
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build();
            prompt.authenticate(info);
        });
    }
}
