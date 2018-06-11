package com.dt3264.deezloader;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.widget.Toast;
import java.io.*;
import java.net.URISyntaxException;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;


public class MainActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("node");
    }

    //We just want one instance of node running in the background.
    public static boolean _startedNodeAlready = false;
    WebView mWebView;
    Socket socket;
    String nodeDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();
        createNotificationChannel();
        mWebView = findViewById(R.id.webView);
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        if (savedInstanceState == null) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            } else {
                iniciaServidor();
            }
        }
        mWebView.setWebViewClient(new HelloWebViewClient());
        mWebView.setWebChromeClient(new WebChromeClient());
    }

    void iniciaServidor() {
        if (!_startedNodeAlready) {
            _startedNodeAlready = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    //The path where we expect the node project to be at runtime.
                    nodeDir = getApplicationContext().getFilesDir().getAbsolutePath() + "/deezerLoader";
                    checkIsApkUpdated();
                    startNodeWithArguments(new String[]{"node",
                            nodeDir + "/app.js"
                    });
                }
            }).start();
            preparaNodeServerListeners();
        } else {
            muestraPagina();
        }
    }

    void preparaNodeServerListeners() {
        try {
            socket = IO.socket("http://localhost:1730");
        } catch (URISyntaxException e) {
        }
        socket.on("siteReady", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                muestraPagina();
            }
        });
        socket.on("progressData", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Integer progress = (Integer) args[0];
                //Log.d("asd", progress.toString());
                if (progress == 100) {
                    //tell system to scan in the song path to add it to the main library
                    File file = new File(fileOnDownload);
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    intent.setData(Uri.fromFile(file));
                    sendBroadcast(intent);
                }
                for (i = fileOnDownload.length() - 1; i >= 0; i--) {
                    if (fileOnDownload.charAt(i) == '/') {
                        index = i;
                        break;
                    }
                }
                actualDownload = fileOnDownload.substring(index + 1);
                creaOActualizaNotification(progress);
            }
        });
        socket.on("pathToDownload", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String obj = (String) args[0];
                fileOnDownload = obj;
            }
        });
        socket.connect();
    }
    Context context;
    String fileOnDownload;
    //Downloaded

    void muestraPagina() {
        context = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WebView webView = findViewById(R.id.webView);
                webView.loadUrl("http://localhost:1730/");
            }
        });
    }

    String CHANNEL_ID = "com.dt3264.Deezloader";
    int NOTIFICATION_ID=100;
    String actualDownload;
    int i, index;
    void creaOActualizaNotification(int progress){
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_notification)
                .setContentTitle("Downloading " + actualDownload)
                .setContentText(progress + "%")
                .setProgress(100, progress, false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        mBuilder.setOnlyAlertOnce(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        // notificationId is a unique int for each notification that you must define
        if(progress<100) notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        else notificationManager.cancel(NOTIFICATION_ID);
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Downloads";
            String description = "Chanel when a song is being downloaded";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 100: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    iniciaServidor();

                } else {
                    // permission denied, boo!
                    Toast.makeText(this, "You should give the permission to use the app", Toast.LENGTH_SHORT).show();
                    this.finish();
                }
            }
        }
    }

    public class HelloWebViewClient extends WebViewClient {
        ProgressDialog progressBar = ProgressDialog.show(MainActivity.this, "Loading", "Please wait...");

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (progressBar.isShowing()) {
                progressBar.dismiss();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        mWebView.saveState(bundle);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        //Toast.makeText(this, "RestoreInstance", Toast.LENGTH_SHORT).show();
        mWebView.restoreState(savedInstanceState);
        muestraPagina();
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native Integer startNodeWithArguments(String[] arguments);

    private void checkIsApkUpdated(){
        if (wasAPKUpdated()) {
            //Recursively delete any existing nodejs-project.
            File nodeDirReference = new File(nodeDir);
            if (nodeDirReference.exists()) {
                deleteFolderRecursively(new File(nodeDir));
            }
            //Copy the node project from assets into the application's data path.
            copyAssetFolder(getApplicationContext().getAssets(), "deezerLoader", nodeDir);
            saveLastUpdateTime();
        }
    }

    private boolean wasAPKUpdated() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("NODEJS_MOBILE_PREFS", Context.MODE_PRIVATE);
        long previousLastUpdateTime = prefs.getLong("NODEJS_MOBILE_APK_LastUpdateTime", 0);
        long lastUpdateTime = 1;
        try {
            PackageInfo packageInfo = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0);
            lastUpdateTime = packageInfo.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return (lastUpdateTime != previousLastUpdateTime);
    }

    private void saveLastUpdateTime() {
        long lastUpdateTime = 1;
        try {
            PackageInfo packageInfo = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0);
            lastUpdateTime = packageInfo.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("NODEJS_MOBILE_PREFS", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("NODEJS_MOBILE_APK_LastUpdateTime", lastUpdateTime);
        editor.apply();
    }

    private static boolean deleteFolderRecursively(File file) {
        try {
            boolean res = true;
            for (File childFile : file.listFiles()) {
                if (childFile.isDirectory()) {
                    res &= deleteFolderRecursively(childFile);
                } else {
                    res &= childFile.delete();
                }
            }
            res &= file.delete();
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean copyAssetFolder(AssetManager assetManager, String fromAssetPath, String toPath) {
        try {
            String[] files = assetManager.list(fromAssetPath);
            boolean res = true;

            if (files.length == 0) {
                //If it's a file, it won't have any assets "inside" it.
                res &= copyAsset(assetManager, fromAssetPath, toPath);
            } else {
                new File(toPath).mkdirs();
                for (String file : files)
                    res &= copyAssetFolder(assetManager, fromAssetPath + "/" + file, toPath + "/" + file);
            }
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toPath) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(fromAssetPath);
            new File(toPath).createNewFile();
            out = new FileOutputStream(toPath);
            copyFile(in, out);
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
