package com.kharcha.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.Base64;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import android.content.pm.PackageInfo;
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
 * Native features for Kharchnama: reading bank SMS from the inbox, hearing new SMS live,
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

    /** Reads text from a bill photo on the phone with ML Kit (no internet needed). */
    @PluginMethod
    public void ocr(PluginCall call) {
        String b64 = call.getString("image");
        if (b64 == null) { call.reject("No image"); return; }
        int comma = b64.indexOf(',');
        if (b64.startsWith("data:") && comma > 0) b64 = b64.substring(comma + 1);
        final Bitmap bmp;
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) { call.reject("Couldn't open the photo"); return; }
        if (bmp == null) { call.reject("Couldn't open the photo"); return; }
        TextRecognizer rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        rec.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener(text -> {
                JSArray lines = new JSArray();
                for (Text.TextBlock b : text.getTextBlocks()) {
                    for (Text.Line l : b.getLines()) {
                        JSObject o = new JSObject();
                        o.put("t", l.getText());
                        Rect r = l.getBoundingBox();
                        if (r != null) { o.put("x", r.left); o.put("y", r.top); o.put("w", r.width()); o.put("h", r.height()); }
                        lines.put(o);
                    }
                }
                JSObject res = new JSObject();
                res.put("text", text.getText());
                res.put("lines", lines);
                res.put("width", bmp.getWidth());
                res.put("height", bmp.getHeight());
                call.resolve(res);
            })
            .addOnFailureListener(e -> call.reject("Couldn't read the bill: " + e.getMessage()));
    }

    /** Google sign-in through Android's Credential Manager. Needs a Web client ID from Google Cloud. */
    @PluginMethod
    public void googleSignIn(PluginCall call) {
        String cid = call.getString("clientId");
        if (cid == null || cid.isEmpty()) { call.reject("Google sign-in isn't set up yet", "no_client"); return; }
        GetGoogleIdOption opt = new GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(cid)
            .setAutoSelectEnabled(false)
            .build();
        GetCredentialRequest req = new GetCredentialRequest.Builder().addCredentialOption(opt).build();
        CredentialManager cm = CredentialManager.create(getContext());
        cm.getCredentialAsync(getActivity(), req, new CancellationSignal(), ContextCompat.getMainExecutor(getContext()),
            new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                @Override
                public void onResult(GetCredentialResponse res) {
                    Credential c = res.getCredential();
                    if (c instanceof CustomCredential && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                        try {
                            GoogleIdTokenCredential g = GoogleIdTokenCredential.createFrom(((CustomCredential) c).getData());
                            JSObject o = new JSObject();
                            o.put("email", g.getId());
                            o.put("name", g.getDisplayName());
                            o.put("givenName", g.getGivenName());
                            o.put("photo", g.getProfilePictureUri() != null ? g.getProfilePictureUri().toString() : null);
                            call.resolve(o);
                        } catch (Exception e) {
                            call.reject("Couldn't read your Google account");
                        }
                    } else {
                        call.reject("Unexpected sign-in result");
                    }
                }
                @Override
                public void onError(GetCredentialException e) {
                    call.reject(e.getMessage() != null ? e.getMessage() : "Sign-in was cancelled", e.getType());
                }
            });
    }

    @PluginMethod
    public void googleSignOut(PluginCall call) {
        CredentialManager cm = CredentialManager.create(getContext());
        cm.clearCredentialStateAsync(new ClearCredentialStateRequest(), new CancellationSignal(), ContextCompat.getMainExecutor(getContext()),
            new CredentialManagerCallback<Void, ClearCredentialException>() {
                @Override public void onResult(Void v) { call.resolve(); }
                @Override public void onError(ClearCredentialException e) { call.resolve(); }
            });
    }

    /** Opens a link (for example the new APK download) in the phone's browser. */
    @PluginMethod
    public void openUrl(PluginCall call) {
        String url = call.getString("url");
        if (url == null) { call.reject("No url"); return; }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("Couldn't open link: " + e.getMessage());
        }
    }

    /** Installed app version, used to decide whether an update needs a new APK. */
    @PluginMethod
    public void appInfo(PluginCall call) {
        JSObject r = new JSObject();
        try {
            PackageInfo pi = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
            r.put("versionName", pi.versionName);
            r.put("build", Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode);
        } catch (Exception e) {
            r.put("build", 0);
        }
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
                .setTitle("Unlock Kharchnama")
                .setSubtitle(reason)
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build();
            prompt.authenticate(info);
        });
    }
}
