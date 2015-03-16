package com.tenforwardconsulting.cordova.bgloc;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class TraxLog {

    private static String TAG = "MILEAGE";

    private static SimpleDateFormat dateFormatter = new SimpleDateFormat("MMMM dd, yyyy hh:mm:ss aa");

    private String name;

    private String detail;

    private Date date;

    public TraxLog(String name, String detail) {
        this.setName(name);
        this.setDetail(detail);
        this.setDate();
    }

    public void setName(String value) {
        this.name = TAG + "_" + value;
    }

    public String getName() {
        return this.name;
    }

    public void setDetail(String value) {
        this.detail = value;
    }

    public String getDetail() {
        return this.detail;
    }

    public Date getDate() {
        return this.date;
    }

    private void setDate() {
        this.date = new Date();
    }

    public JSONObject toJSON(String userEmailAddress) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("name", this.getName());
            obj.put("detail", this.getDetail());
            obj.put("date", dateFormatter.format(this.getDate()));
            obj.put("user", userEmailAddress);
            return obj;
        } catch (JSONException e) {
            return null;
        }
    }

    public static String serialize(TraxLog[] logs, String userEmailAddress) {
        JSONArray jsonLogs = new JSONArray();
        for (TraxLog log : logs) {
            jsonLogs.put(log.toJSON(userEmailAddress));
        }
        try {
            JSONObject jsonLogsObj = new JSONObject();
            jsonLogsObj.put("logs", jsonLogs);
            return jsonLogsObj.toString();
        } catch (JSONException e) {
           return null;
        }
    }
}

