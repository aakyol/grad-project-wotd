package com.wotd_app.WOTD;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Semaphore;
import java.util.prefs.Preferences;

public class WOTD_Main extends Activity {

    String lastSelected = "";
    public static boolean appActive = false;
	
    @Override
    public void onStart() { //Check methods of activity's active/deactive status
        super.onStart();
        System.out.println("Main UI: UI is opened.");
        appActive = true;
    }

    @Override
    public void onStop() { //Check methods of activity's active/deactive status
        super.onStop();
        System.out.println("Main UI: UI is closed.");
        appActive = false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        System.out.println("Main UI: onCreate started.");

        PackageManager m = getPackageManager();
        String packageName = getPackageName();
        PackageInfo packageInfo = null;
        try {
            packageInfo = m.getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            System.out.println(e);
        }

        Spinner mainSpinner = (Spinner) findViewById(R.id.spinner);

        String[] text = {"English", "French", "Italian", "German", "Spanish"}; //Setting the spinner for languages
        ListeningArrayAdapter<CharSequence> adapter = new ListeningArrayAdapter<CharSequence>(this, R.layout.spinner_item, text);
        adapter.setDropDownViewResource(R.layout.spinner_item);
        adapter.addSpinnerListener(new SpinnerListener() {

            @Override
            public void onSpinnerExpanded() {
                System.out.println("MainUI: spinner expanded.");

                TextView display = (TextView) findViewById(R.id.textView);
                Spinner spinner = (Spinner) findViewById(R.id.spinner);

                setTopDisplay(display.getPaddingTop(), spinner.getHeight() * 4); //Padding adjuesment to make the text
                                                                                 // visible while having the spinner open
            }

            @Override
            public void onSpinnerCollapsed() {
                System.out.println("MainUI: spinner collapsed.");
                setTopDisplay(0,0); //Re-adjusting the padding of the text
            }
        });
        mainSpinner.setAdapter(adapter);

        mainSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int pos, long id) {


                String selectedItem = parent.getItemAtPosition(pos).toString();

                TextView display = (TextView) findViewById(R.id.textView);
                display.setMovementMethod(new ScrollingMovementMethod());
                display.setText("Fetching data...");

                if (selectedItem == "English") { 
                    englishCrawler eCrawl = new englishCrawler();
                    eCrawl.execute();
                } else {
                    europeCrawler eCrawl = new europeCrawler();
                    eCrawl.execute(selectedItem, "", "");
                }
                lastSelected = selectedItem;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

                //TODO AUTO-GEN Method. Does nothing.
            }

        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this); //Locallly storing the notification boolean on a shared preference, so that 
        SharedPreferences.Editor editPrefs = prefs.edit();                             //when the application is reopened, the data will be persistent
        editPrefs.clear();
        editPrefs.putBoolean("notified", false);
        editPrefs.commit();

        Intent intent = new Intent(getApplicationContext(), AlarmReceiver.class);
        intent.setAction("packagename.ACTION");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(),
                0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(pendingIntent);
        alarm.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), 1000*60, pendingIntent); //Setting an AlarmListener for checking the time which is required
																										 //to set notifications and activate/deactivate the data service

    }


    private class englishCrawler extends AsyncTask<String, String, String> { //Crawler method for the english languages. Seperated from the other languages' method since
																			 //it is using a different website for the words
        @Override
        protected String doInBackground(String... params) {
            Document doc = null;
            StringBuilder result = new StringBuilder();
            try {

                doc = Jsoup.connect("http://www.merriam-webster.com/word-of-the-day/").get();
                org.jsoup.select.Elements paragraphs = doc.getElementsByClass("word-and-pronunciation");
                org.jsoup.select.Elements paragraphs2 = doc.getElementsByClass("wod-definition-container");

                // gets the word and filters unwanted string values, then appends it to the result
                for (Element w : paragraphs) {
                    String text = w.text();
                    text = text.replace(" play", "");
                    result.append(text + "\n");
                }

                for (Element w2 : paragraphs2) { // gets the definition and appends it to the result

                    String text = w2.text();
                    result.append(text + "\n");
                }

                String[] resultClipped = result.toString().split(" ");

                // the info gathered from the website is splitted into words,
                // so they can be filtered further.

                result = null;
                result = new StringBuilder();

                for(String s: resultClipped)
                {
                    if(s.equals("Examples")) // if the word is Examples, we'll append ":" for better ux
                    {
                        result.append("\n" + s + " : " + "\n");
                    }
                    else{
                        result.append(s + " "  );
                    }
                }
                System.out.println("MainUI (englishCrawler): Crawl and display successful.");
                return result.toString();

            } catch (IOException e) {
                System.out.println("MainUI (englishCrawler): General exception caught. Exception details: "
                        + "\n" + e);
                return "";
            }
        }

        protected void onPostExecute(String result) {
            TextView display = (TextView) findViewById(R.id.textView);
            display.setMovementMethod(new ScrollingMovementMethod());
            if (result != "") {
                display.setText(result);
            } else {
                display.setText("Cannot fetch from server. Please restart the application.");
            }
        }
    }

    private class europeCrawler extends AsyncTask<String, String, String> { //Crawler method for the english languages. Remaining 4 languages' words crawled via this method
																			//since the website for the words is the same
        @Override
        protected String doInBackground(String... params) {

            Document doc = null;

            try {

                DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
                Date date = new Date();

                String dateToday = dateFormat.format(date);


                String url = "http://www.bitesizedlanguages.com/wod/r/" + params[0] + "/" + dateToday;

                doc = Jsoup.connect(url).get();
                Elements word1 = doc.getElementsByTag("h1");
                Elements word2 = doc.getElementsByTag("p");

                StringBuilder result = new StringBuilder();

                for (Element w : word1)
                    result.append(w.text());

                result.append("\n");
                // print the sentences
                for (int i = 0; i < ((Elements) word2).size(); i++) {
                    if (i == 1) {
                        String line = ((Elements) word2).eq(i).text(); // read the line

                        String[] lineSplitted = line.split(": ");
                        String mark = lineSplitted[0].substring(lineSplitted[0].length() - 1, lineSplitted[0].length());

                        // get the last digit of the first sentence, which is the ./?/!
                        for (int m = 0; m < lineSplitted.length; m++) {
                            // clear text after marks
                            if (lineSplitted[m].contains(mark)) {
                                lineSplitted[m] = lineSplitted[m].substring(0, lineSplitted[m].lastIndexOf(mark));
                                result.append(lineSplitted[m] + mark + "\n");
                            } else
                                continue;
                        }


                    } else
                        continue;

                }
                System.out.println("MainUI (europeCrawler): Crawl and display successful.");
                return result.toString();

            } catch (IOException ex) {
                ex.printStackTrace();
                System.out.println("MainUI (europeCrawler): Internet issues? Please check. Eexception details: "
                        + "\n" + ex);
                return "";
            } catch (Exception ex) {
                System.out.println("MainUI (europeCrawler): General exception caught. Exception details: "
                        + "\n" + ex);
                return "";
            }
        }

        protected void onPostExecute(String result) {
            TextView display = (TextView) findViewById(R.id.textView);
            display.setMovementMethod(new ScrollingMovementMethod());
            if (result != "") {
                display.setText(result);
            } else {
                display.setText("Cannot fetch from server. Please restart the application.");
            }
        }
    }

    public void setTopDisplay(int top, int spinheight) //Padding method for spinner
    {
        TextView display = (TextView) findViewById(R.id.textView);
        Spinner spinner = (Spinner) findViewById(R.id.spinner);
        System.out.println("Before\n" + display.getPaddingLeft() + " " + (display.getPaddingTop() + " " + spinner.getHeight()) + " " + " " +
                display.getPaddingRight() + " " + display.getPaddingBottom()); //DEBUG
        display.setPadding(display.getPaddingLeft(), (top + spinheight),
                display.getPaddingRight(), display.getPaddingBottom());
        System.out.println("After\n" + display.getPaddingLeft() + " " + (display.getPaddingTop() + " " + spinner.getHeight()) + " " + " " +
                display.getPaddingRight() + " " + display.getPaddingBottom()); //DEBUG
    }

}


