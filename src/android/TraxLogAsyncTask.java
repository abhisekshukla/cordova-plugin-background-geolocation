package com.tenforwardconsulting.cordova.bgloc;

import android.os.AsyncTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.NameValuePair;

public class TraxLogAsyncTask extends AsyncTask<String, Void, HttpResponse> {

    private IOException ex;

    protected HttpResponse doInBackground(String... infos) {
        String userEmailAddress = "test+driving@hurdlr.com";
        String name = infos[0];
        String detail = infos[1];
        // Create a new HttpClient and Post Header
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost("http://dev.hurdlr.com/rest/v1/misc/log");

        try {
            // Add your data
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
            nameValuePairs.add(new BasicNameValuePair("name", "MILEAGE_" + name));
            nameValuePairs.add(new BasicNameValuePair("detail", detail + userEmailAddress));
            httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

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