package com.tenforwardconsulting.cordova.bgloc;

import android.os.AsyncTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.entity.StringEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.DefaultHttpClient;

import org.json.JSONArray;

public class TraxLogAsyncTask extends AsyncTask<TraxLog, Void, HttpResponse> {

    public static String userEmailAddress = "noaddress@hurdlr.com";
    public static String baseUrl = "";

    private IOException ex;

    protected HttpResponse doInBackground(TraxLog... logs) {
        String logsString = TraxLog.serialize(logs, userEmailAddress);

        // Create a new HttpClient and Post Header
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost(baseUrl + "/misc/logs");
        httppost.setHeader("Content-type", "application/json");
        try {
            // Add your data
            httppost.setEntity(new StringEntity(logsString));

            // Execute HTTP Post Request
            return httpclient.execute(httppost);

        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            return null;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            this.ex = e;
            System.out.println(this.ex.getMessage());
            return null;
        }
    }

    protected void onPostExecute(String x) {
        // TODO: check this.exception
        // TODO: do something with the feed
    }
}