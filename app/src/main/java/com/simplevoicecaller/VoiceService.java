package com.simplevoicecaller;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telecom.TelecomManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Locale;

public class VoiceService extends Service implements RecognitionListener {

    private static final String TAG = "VoiceService";
    private static final String CHANNEL_ID = "voice_caller_channel";
    private static final int NOTIF_ID = 2001;

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private Handler handler;
    private boolean isListening = false;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        setupRecognizer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification("Listening...", "Bol 'Hey Jarvis' phir command"));
        startListening();
        return START_STICKY;
    }

    private void setupRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(this);

            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN"); // Hindi + English mix
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        } else {
            Log.e(TAG, "Speech recognition not available");
        }
    }

    private void startListening() {
        if (speechRecognizer == null || isListening) return;
        try {
            isListening = true;
            speechRecognizer.startListening(recognizerIntent);
            Log.d(TAG, "Started listening");
        } catch (Exception e) {
            Log.e(TAG, "startListening failed", e);
            isListening = false;
            restartListeningDelayed(1500);
        }
    }

    private void restartListeningDelayed(long delayMs) {
        handler.postDelayed(() -> {
            isListening = false;
            startListening();
        }, delayMs);
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        Log.d(TAG, "Ready for speech");
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.d(TAG, "Speech began");
    }

    @Override
    public void onRmsChanged(float rmsdB) {}

    @Override
    public void onBufferReceived(byte[] buffer) {}

    @Override
    public void onEndOfSpeech() {
        Log.d(TAG, "End of speech");
        isListening = false;
    }

    @Override
    public void onError(int error) {
        String msg = getErrorText(error);
        Log.w(TAG, "Error: " + msg);
        isListening = false;
        // Always restart
        restartListeningDelayed(800);
    }

    @Override
    public void onResults(Bundle results) {
        isListening = false;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String spoken = matches.get(0).toLowerCase(Locale.ROOT).trim();
            Log.i(TAG, "Heard: " + spoken);
            processCommand(spoken);
        }
        restartListeningDelayed(600);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {}

    @Override
    public void onEvent(int eventType, Bundle params) {}

    private void processCommand(String spoken) {
        // Wake word check
        boolean hasWake = spoken.contains("jarvis") || spoken.contains("जार्विस") || spoken.contains("जारविस")
                || spoken.contains("hey jarvis") || spoken.contains("हे जार्विस") || spoken.contains("हे जारविस");

        if (!hasWake) {
            Log.d(TAG, "No wake word, ignoring");
            return;
        }

        // Remove wake word part roughly
        String cmd = spoken
                .replace("hey jarvis", "")
                .replace("हे जार्विस", "")
                .replace("हे जारविस", "")
                .replace("jarvis", "")
                .replace("जार्विस", "")
                .replace("जारविस", "")
                .trim();

        Log.i(TAG, "Command after wake: " + cmd);

        // End / Cut call
        if (cmd.contains("end call") || cmd.contains("cut call") || cmd.contains("hang up")
                || cmd.contains("call kaat") || cmd.contains("call cut") || cmd.contains("call band")
                || cmd.contains("कॉल काट") || cmd.contains("कॉल कट") || cmd.contains("कॉल बंद")) {
            endCall();
            showToast("Call cut karne ki koshish...");
            return;
        }

        // Last / Recent call
        if (cmd.contains("last call") || cmd.contains("recent call") || cmd.contains("last number")
                || cmd.contains("jo abhi") || cmd.contains("abhi wali") || cmd.contains("call history")
                || cmd.contains("पिछली कॉल") || cmd.contains("अभी वाली") || cmd.contains("लास्ट कॉल")) {
            String number = getMostRecentCallNumber();
            if (number != null && !number.isEmpty()) {
                makeCall(number);
                showToast("Last call par call laga raha hoon: " + number);
            } else {
                showToast("Koi recent call nahi mili");
            }
            return;
        }

        // Normal call command: "call X" or "X ko call karo"
        if (cmd.contains("call") || cmd.contains("कॉल")) {
            String target = extractTarget(cmd);
            if (target != null && !target.isEmpty()) {
                // First try as number
                if (target.matches("[0-9+\\-\\s]{6,}")) {
                    makeCall(target.replaceAll("[^0-9+]", ""));
                    showToast("Calling " + target);
                } else {
                    // Try contact name
                    String number = getNumberFromContactName(target);
                    if (number != null) {
                        makeCall(number);
                        showToast("Calling " + target);
                    } else {
                        // Fallback: try dialing the spoken text as number-ish
                        showToast("Contact nahi mila: " + target);
                    }
                }
            } else {
                showToast("Kis ko call karna hai?");
            }
        }
    }

    private String extractTarget(String cmd) {
        // Simple extraction
        String cleaned = cmd
                .replace("call", "")
                .replace("कॉल", "")
                .replace("karo", "")
                .replace("kar do", "")
                .replace("lagao", "")
                .replace("लगाओ", "")
                .replace("को", "")
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private void makeCall(String number) {
        try {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + number));
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(callIntent);
            Log.i(TAG, "Calling: " + number);
        } catch (SecurityException e) {
            Log.e(TAG, "No CALL_PHONE permission", e);
            showToast("CALL_PHONE permission nahi hai");
        } catch (Exception e) {
            Log.e(TAG, "Call failed", e);
            showToast("Call nahi lag paya");
        }
    }

    private void endCall() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
                if (tm != null) {
                    boolean success = tm.endCall();
                    Log.i(TAG, "endCall result: " + success);
                    if (!success) {
                        showToast("Call cut nahi hua (permission/device limit)");
                    }
                }
            } else {
                showToast("Is Android version par end call support nahi");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "endCall security", e);
            showToast("End call permission nahi mili");
        } catch (Exception e) {
            Log.e(TAG, "endCall failed", e);
            showToast("Call cut nahi ho paya");
        }
    }

    private String getMostRecentCallNumber() {
        try {
            Cursor cursor = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.DATE},
                    null, null,
                    CallLog.Calls.DATE + " DESC"
            );
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    String number = cursor.getString(0);
                    cursor.close();
                    return number;
                }
                cursor.close();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "No READ_CALL_LOG", e);
            showToast("Call log permission nahi hai");
        } catch (Exception e) {
            Log.e(TAG, "Call log error", e);
        }
        return null;
    }

    private String getNumberFromContactName(String name) {
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(name));
            Cursor cursor = getContentResolver().query(uri,
                    new String[]{ContactsContract.Contacts._ID},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                String contactId = cursor.getString(0);
                cursor.close();

                Cursor phones = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        new String[]{contactId}, null);

                if (phones != null && phones.moveToFirst()) {
                    String number = phones.getString(0);
                    phones.close();
                    return number;
                }
                if (phones != null) phones.close();
            }
            if (cursor != null) cursor.close();
        } catch (Exception e) {
            Log.e(TAG, "Contact lookup failed", e);
        }
        return null;
    }

    private void showToast(final String msg) {
        handler.post(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show());
    }

    private String getErrorText(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO: return "Audio error";
            case SpeechRecognizer.ERROR_CLIENT: return "Client error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Permissions";
            case SpeechRecognizer.ERROR_NETWORK: return "Network";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH: return "No match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Busy";
            case SpeechRecognizer.ERROR_SERVER: return "Server";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "Speech timeout";
            default: return "Unknown error " + errorCode;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Voice Caller Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background voice listening");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String title, String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pending)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        handler.removeCallbacksAndMessages(null);
        Log.i(TAG, "Service destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
