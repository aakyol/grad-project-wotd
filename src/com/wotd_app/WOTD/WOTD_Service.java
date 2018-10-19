package com.wotd_app.WOTD;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Patterns;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Semaphore;

//For Dropbox Try 1
//import com.dropbox.client2.DropboxAPI;
//import com.dropbox.client2.android.AndroidAuthSession;

/**
 * Created by aydinakyol on 21.11.2015.
 */
public class WOTD_Service extends Service implements SensorEventListener { //The service which collects and uploads user data between 4pm-8pm

    Semaphore sensorData = new Semaphore(1); //Implemented to prevent data inconsistency

    boolean threadRunning = true;
    static boolean serviceRunning;

    SensorManager sensorManager = null;

    File dataInSensorFile = null;
    OutputStream dataInSensorFileStream = null;
    File authJson = null;
    OutputStream authJsonStream = null;


    AccountManager manager = null;
    Account[] accounts = null;
    String accName = null;

    String root = null;
    File folder = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        System.out.println("WOTD_Service: Service started.");
        serviceRunning = true;
        root = Environment.getExternalStorageDirectory().toString(); //Reaching external storage and creating a folder to store the collected data before upload
        folder = new File(root + "/WOTD");
        if (!folder.exists()) {
            folder.mkdir();
        }

        try {
            final Account[] accounts = AccountManager.get(this).getAccounts(); //Fetching account emails to use as ids in data files
            for (Account account : accounts) {
                if (Patterns.EMAIL_ADDRESS.matcher(account.name).matches()) {
                    accName = account.name;
                }
            }
        } catch (Exception ex) {
            System.out.println(ex.toString());
            accName = "UNKNOWNDEVICE";
        }

        System.out.println("WOTD_Service: Device id is: " + accName);

        new Thread(){ //The thread for upload process. Will check if the data file reached its max. size limit, and will send if exceeded the limit.
            public void run(){ 
                while(threadRunning) { //If the thread going to be destroyed (onDestroy()), the thread will be stopped
                    if (true){
                        try {
                            sensorData.acquire(); //locking semaphore so any write will not interfere the data's consistency
                            if (dataInSensorFileStream != null) {
                                long filesize = getFolderSize(dataInSensorFile) / 1024; //Fetching filesize in KB

                                if (filesize >= 10240 && filesize < 12288) { //Upload limit in KB. Limit is currently between 10MB and 12MB
                                    System.out.println("WOTD_Service (UploadThread): Data file reached max. size limit by "
                                            + filesize / 1024 + " MB, uploading by Dropbox whenever possible...");
                                    System.out.println("WOTD_Service (UploadThread): Situation report: " + wifiConnected() + " " + WOTD_Main.appActive);
                                    if (wifiConnected() && !WOTD_Main.appActive) { //If a wifi connection is available AND the activity of the application is onPause, upload can be done,
																				   //since 10MB file cannot be uploaded via mobile network and a thread doing loads of work can freeze the gui
                                        System.out.println("WOTD_Service (UploadThread): WIFI found and the Activity is " +
                                                "not active, uploading file...");

                                        DbxUploader uploader = new DbxUploader("/" + authJson); //Dropbox uploader class object with our JSON file
                                        System.out.println("WOTD_Service (UploadThread): Dropbox authentication successful. Uploading...");
                                        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                                        Date date = new Date();
                                        String formattedDate = dateFormat.format(date).toString().replace('/', '-'); //The current time to seperate files in order of their arrival
                                        uploader.upload(root + "/WOTD/" + accName + "_wotdFiles.csv", "/" + accName + "/" +
                                                formattedDate + "_datasent.csv");


                                        dataInSensorFile.delete(); //deleting the file and creating a new one to continue data collection
                                        dataInSensorFile = new File(root + "/WOTD/" + accName + "_wotdFiles.csv");
                                        dataInSensorFile.createNewFile();
                                        dataInSensorFileStream = new FileOutputStream(dataInSensorFile, true);

                                        System.out.println("WOTD_Service (UploadThread): Data sent. Old record file deleted.");
                                    } else {
                                        System.out.println("WOTD_Service (UploadThread): Requirements did not met. Checking again in 3 seconds.");
                                    }
                                } else if (filesize >= 12288) { //If this condition succeeds, then either the conditions for upload did not met or the application is relaunched before an upload commences
                                    System.out.println("WOTD_Service (UploadThread): File is bigger " +
                                            "(" + filesize / 1024 + "MB) than the limit. Possible leftover data, deleting file.");
                                    dataInSensorFile.delete(); //deleting the file since it exceeds the filesize limit
                                    dataInSensorFile = new File(root + "/WOTD/" + accName + "_wotdFiles.csv");
                                    dataInSensorFile.createNewFile();
                                    dataInSensorFileStream = new FileOutputStream(dataInSensorFile, true);
                                    System.out.println("WOTD_Service (UploadThread): New data file created.");

                                } else {
                                    System.out.println("WOTD_Service (UploadThread): File is too small (" + filesize / 1024 + "MB) to upload.");
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("WOTD_Service: General exception caught. Exception details: " + e);
                        } finally {
                            sensorData.release();
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    else {
                        try {
                            System.out.println("WOTD_Service (UploaderThread): Sleeping...");
                            Thread.sleep(3600000); //Checking the conditions every 6 minutes, since constant checks freezes the gui
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

        }.start();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        PackageManager packageManager = getPackageManager();
        String packageName = getPackageName();
        PackageInfo packageInfo = null;

        try {
            packageInfo = packageManager.getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            System.out.println(e);
        }

        try {

            dataInSensorFile = new File(root + "/WOTD/" + accName + "_wotdFiles.csv"); //Initial data file construction
            authJson = new File(root + "/WOTD/" + "authJson" + ".json"); //Same construction for authentication credentials file. Used for Dropbox uploader object in uploader thread
            if (!dataInSensorFile.exists()) {
                try {
                    dataInSensorFile.createNewFile(); //If there is no file created, creates a new one
                    System.out.println("WOTD_Service: New data file created");
                } catch (IOException e) {
                    System.out.println(e);
                }
            }
            if (!authJson.exists()) {
                try {
                    authJson.createNewFile();
                    System.out.println("WOTD_Service: New JSON authenticator file created");
                } catch (IOException e) {
                    System.out.println(e);
                }
            }
        }
        catch (Exception e) {
            System.out.println(e);
        }
        try {
            authJsonStream = new FileOutputStream(authJson);
            String toWrite = "{" + String.format("%n") + "\"key\": \"APP_KEY\"," + String.format("%n") 
                    + "\"secret\": \"APP_SECRET\"," + String.format("%n")
                    + "\"access_token\": \"ACCESS_TOKEN\""
                    + String.format("%n") + "}"; 
					 //APP_KEY and APP_SECRET are the key and secret values, which are necessary credentials to send the data to our dropbox account
					 //We deleted our own credentials from the code. You must enter your credentials to APP_KEY, APP_SECRET and ACCESS_TOKEN above
					 //The document provides how to get credentials for an android application
            authJsonStream.write(toWrite.getBytes(), 0, toWrite.getBytes().length);
            authJsonStream.flush();
            authJsonStream.close();
        } catch (FileNotFoundException e) {
            System.out.println(e);
        } catch (IOException e) {
            System.out.println(e);
        }

        try {
            dataInSensorFileStream = new FileOutputStream(dataInSensorFile, true);
        } catch (Exception e) {
            System.out.println(e);
        }


        //All sensors that used in rendering the cube that represents the orientation of the phone.
        Sensor gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        Sensor linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        Sensor rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        sensorManager.registerListener(this, gyroscope,SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, gravity,SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, linearAcceleration,SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, rotationVector,SensorManager.SENSOR_DELAY_FASTEST);

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onSensorChanged(SensorEvent event) {

        long filesize = getFolderSize(dataInSensorFile) / 1024;

        if(filesize < 10240) {
            double x = event.values[0]; //Raw X,Y and Z coordinate values from the sensor
            double y = event.values[1];
            double z = event.values[2];
            long timestamp = event.timestamp; //Timestamp in nanoseconds

            int sensorAttributeValue = -1;

            if (event.sensor.getType() == 4) { //Delivering the sensor types with integer id.
                sensorAttributeValue = 0;
            } else if (event.sensor.getType() == 9) {
                sensorAttributeValue = 1;
            } else if (event.sensor.getType() == 10) {
                sensorAttributeValue = 2;
            } else {
                sensorAttributeValue = 3;
            }

            String toWrite = sensorAttributeValue + "," + Double.toString(x) + "," + Double.toString(y) + "," + Double.toString(z) + "," + timestamp + accName + "\n";

            try {
                sensorData.acquire(); //Checking if the semaphore is available (i.e. checking if any upload is ongoing on the file)
                dataInSensorFileStream.write(toWrite.getBytes(), 0, toWrite.getBytes().length);
                dataInSensorFileStream.flush();
                sensorData.release();

            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
		//AUTO-GEN method. 
    }

    public static long getFolderSize(File f) { //Folder size calculator
        long size = 0;
        if (f.isDirectory()) {
            for (File file : f.listFiles()) {
                size += getFolderSize(file);
            }
        } else {
            size=f.length();
        }
        return size;
    }

    public boolean wifiConnected() //Wifi connection checker
    {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected() && networkInfo.getTypeName().equals("WIFI"));
    }

    @SuppressLint("ServiceCast")
    @Override
    public void onDestroy() {
        threadRunning = false;
        serviceRunning = false;
        sensorManager.unregisterListener((SensorEventListener) getSystemService(Context.SENSOR_SERVICE));
        sensorManager = null;
        super.onDestroy();
    }

}