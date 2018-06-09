package com.dt3264.deezloader;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

public class CheckServerConnection extends IntentService {
    /**
     * A constructor is required, and must call the super IntentService(String)
     * constructor with a name for the worker thread.
     */
    public CheckServerConnection() {
        super("HelloIntentService");
    }

    /**
     * The IntentService calls this method from the default worker thread with
     * the intent that started the service. When this method returns, IntentService
     * stops the service, as appropriate.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        // Normally we would do some work here, like download a file.
        // For our sample, we just sleep for 5 seconds.
        checkServerStatus();
    }

    static AsyncTask<Void, Void, String> asyncTask(final Context context){
        return new AsyncTask<Void,Void,String>() {
            @Override
            protected String doInBackground(Void... params) {
                String nodeResponse="";
                try {
                    URL localNodeServer = new URL("http://localhost:1730/");
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(localNodeServer.openStream()));
                    String inputLine;
                    while ((inputLine = in.readLine()) != null)
                        nodeResponse=nodeResponse+inputLine;
                    in.close();
                } catch (Exception ex) {
                    nodeResponse=ex.toString();
                }
                return nodeResponse;
            }
            @Override
            protected void onPostExecute(String result) {
                if(!result.contains("DOCTYPE")) {
                    Intent intent = new Intent();
                    intent.setAction("Check_Server_Status");
                    intent.putExtra("data","Bad");
                    context.sendBroadcast(intent);
                }
                else{
                    Intent intent = new Intent();
                    intent.setAction("Check_Server_Status");
                    intent.putExtra("data","Good");
                    context.sendBroadcast(intent);

                }
            }
        }.execute();
    }

    void checkServerStatus(){
        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException e){}
        asyncTask(this);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();
        return super.onStartCommand(intent,flags,startId);
    }
}