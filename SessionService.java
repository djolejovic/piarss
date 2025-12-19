package djordje.jovic;


import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class SessionService extends Service {

    private static final String TAG = "SessionService";
    private static final String CHANNEL_ID = "session_channel";
    private static final String PREFS = "session_service_prefs";

    private final IBinder binder = new LocalBinder();
    private Thread workerThread;
    private boolean running = false;
    private DatabaseHelper dbHelper;

    public class LocalBinder extends Binder {
        SessionService getService() {
            return SessionService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        dbHelper = new DatabaseHelper(this);
        running = true;

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    checkSessionsAndNotify();
                    Thread.sleep(60_000); // 1 minut
                } catch (InterruptedException e) {
                    Log.e(TAG, "Thread interrupted", e);
                    running = false;
                }
            }
        });
        workerThread.start();

        Log.d(TAG, "Service created and worker thread started.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
        Log.d(TAG, "Service destroyed.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void checkSessionsAndNotify() {
        long now = System.currentTimeMillis();
        long fifteenMin = 15 * 60 * 1000;
        long sixteenMin = 16 * 60 * 1000;

        Cursor cursor = dbHelper.getReadableDatabase().query(
                "SESSIONS",
                null,
                "EndOfVoting BETWEEN ? AND ?",
                new String[]{String.valueOf(now + fifteenMin), String.valueOf(now + sixteenMin)},
                null,
                null,
                null
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("ID"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("SessionName"));
                long end = cursor.getLong(cursor.getColumnIndexOrThrow("EndOfVoting"));

                String key = "notified_" + id + "_" + end;
                SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                if (!prefs.getBoolean(key, false)) {
                    sendNotification(name);
                    prefs.edit().putBoolean(key, true).apply();
                    Log.d(TAG, "Notification sent for session: " + name);
                }
            }
            cursor.close();
        }
    }

    private void sendNotification(String sessionName) {
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Session Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Obaveštenja o sesijama");
            mNotificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(sessionName)
                .setContentText("Uskoro ističe vreme za glasanje (15 minuta).")
                .setAutoCancel(true);

        mNotificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
