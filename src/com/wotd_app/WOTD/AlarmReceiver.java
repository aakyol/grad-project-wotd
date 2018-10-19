package com.wotd_app.WOTD;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import java.util.Calendar;

/**
 * Created by aydinakyol on 01.04.2016.
 */
public class AlarmReceiver extends BroadcastReceiver {

    public boolean notified = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute =  c.get(Calendar.MINUTE);
        Intent serviceIntent = new Intent(context, WOTD_Service.class);
        System.out.println("AlarmListener: Current time is: " + hour + " " + minute);
        if(hour > 15 && hour < 20) //Between 4pm and 8pm, the data service will be active.
        {
            if(!WOTD_Service.serviceRunning) {
                context.startService(serviceIntent);
            }
        }
        if(hour > 19) //After 8pm, the data service will be shut down
        {
            if(WOTD_Service.serviceRunning) {
                context.stopService(serviceIntent);
            }
        }
        if(hour == 11) //At 11am, a notification for new words will be given to the user. This makes sure that the user
                       // use the application one every day, which is enough to run the data service
        {
            try {
                notified = prefs.getBoolean("notified", false);
                System.out.println("AlarmListener: Notification boolean is: " + notified);
                if (!notified) { //If a notification is already displayed, there will be no notifications untill the next day
                    SharedPreferences.Editor editPrefs = prefs.edit();
                    editPrefs.clear();
                    editPrefs.putBoolean("notified", true); //Setting the notification boolean
                    editPrefs.commit();
                    NotificationCompat.Builder nBuilder = new NotificationCompat.Builder(context) //Building a notification
                            .setSmallIcon(R.drawable.ic_launcher_custom)
                            .setContentTitle("Hey, New Words!")
                            .setStyle(new NotificationCompat.BigTextStyle().bigText("Launch the application to learn today's words!"))
                            .setAutoCancel(true);
                    Intent notifyIntent = new Intent(context, WOTD_Main.class);
                    PendingIntent notifyIntentPending = PendingIntent.getActivity(context, 1, notifyIntent, 0);
                    nBuilder.setContentIntent(notifyIntentPending);
                    NotificationManager mNotificationManager =
                            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    mNotificationManager.notify(1, nBuilder.build());

                }
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
        if(hour != 11)
        {
            SharedPreferences.Editor editPrefs = prefs.edit();
            editPrefs.clear();
            editPrefs.putBoolean("notified", false); //The boolean reset for the notification which will be displayed next day
            editPrefs.commit();
        }
    }
}
