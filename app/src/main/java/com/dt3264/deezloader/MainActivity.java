package com.dt3264.deezloader;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.EnvironmentCompat;
import android.support.v4.provider.DocumentFile;
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

import com.obsez.android.lib.filechooser.ChooserDialog;
import com.obsez.android.lib.filechooser.internals.FileUtil;
import com.snatik.storage.Storage;

import java.io.*;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

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
    SharedPreferences sharedPreferences;
    Storage storage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();
        mWebView = findViewById(R.id.webView);
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        createNotificationChannel();
        sharedPreferences = getPreferences(Context.MODE_PRIVATE);
        storage = new Storage(getApplicationContext());
        if (savedInstanceState == null) {
            compruebaPermisos();
        }
    }

    void compruebaPermisos(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        } else {
            iniciaServidor();
        }
    }

    void iniciaServidor() {
        mWebView.setWebViewClient(new HelloWebViewClient());
        mWebView.setWebChromeClient(new WebChromeClient());
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
        } catch (URISyntaxException e) { }
        socket.on("siteReady", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                muestraPagina();
            }
        });
        socket.on("checkIfHasNewPath", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String magicUri = sharedPreferences.getString(SHARED_PREFS_NEW_PATH, "");
                if (!magicUri.equals("")) {
                    //Get uri and send it to the app to show it instead the internal path
                    Uri treeUri = Uri.parse(magicUri);
                    String realPath = treeUri.getPath().replace("tree", "storage").replace(":", "/");
                    if(!realPath.endsWith("/")) realPath+="/";
                    socket.emit("newPath", realPath);
                }
            }
        });
        socket.on("newVersion", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String url = (String)args[0];
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });
        socket.on("requestNewPath", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 1234);
            }
        });
        socket.on("useDefaultPath", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                sharedPreferences.edit().putString(SHARED_PREFS_NEW_PATH, "").apply();
            }
        });
        socket.on("progressData", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Integer progress = (Integer) args[0];
                notificaDescarga(progress);
            }
        });
        socket.on("fetchingSongData", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                notificaDescarga(0);
            }
        });
        socket.on("pathToDownload", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                internalPath = (String) args[0];
                songName = (String) args[1];
                fileName = internalPath.substring(internalPath.lastIndexOf("/")+1);
            }
        });
        socket.on("cancelDownload", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                notificaDescargaCancelada();
                internalPath = null;
                songName = null;
            }
        });
        socket.on("alreadyDownloaded", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String song = (String) args[0];
                notificaYaDescargado(song);
                internalPath = null;
                songName = null;
            }
        });
        socket.on("downloadReady", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                //tell system to scan in the song path to add it to the main library
                File internalFile;
                String magicUri = sharedPreferences.getString(SHARED_PREFS_NEW_PATH, "");
                if(!magicUri.equals("")) {
                    //Get uri and get permissions for the external dir
                    Uri treeUri = Uri.parse(magicUri);
                    DocumentFile pickedDir = DocumentFile.fromTreeUri(getBaseContext(), treeUri);
                    grantUriPermission(getPackageName(), treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    //Copy file with dir with permissions given
                    copyFile("/storage/emulated/0/Music/", fileName, fileName, pickedDir);
                    //delete source file after the copy was completed
                    internalFile = new File(internalPath);
                    internalFile.delete();
                    //and scan the output file
                    File externalFile = new File("/storage/" + pickedDir.getName() + "/" + fileName);
                    scanNewSongExternal(externalFile);
                }
                else {
                    internalFile = new File(internalPath);
                    scanNewSongInternal(Uri.fromFile(internalFile));
                }
                internalPath = null;
                songName = null;
            }
        });
        socket.connect();
    }

    void scanNewSongInternal(Uri fileUri){
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(fileUri);
        sendBroadcast(intent);
    }

    void scanNewSongExternal(File externalFile){
        MediaScannerConnection.scanFile(getBaseContext(), new String[] { externalFile.toString() }, null, new MediaScannerConnection.OnScanCompletedListener() {
            public void onScanCompleted(String path, Uri uri) {
                Log.i("ExternalStorage", "Scanned " + path + ":");
                Log.i("ExternalStorage", "-> uri=" + uri);
            }
        });
    }

    String SHARED_PREFS_NEW_PATH = "newPath";

    Context context;
    String internalPath, songName, fileName;

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
    int i;
    void notificaDescarga(int progress){
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_notification)
                .setContentTitle(songName!=null ? ("Downloading: " + songName) : "Getting track info")
                .setSubText(progress + "%")
                .setProgress(100, progress, (progress==0))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        mBuilder.setOnlyAlertOnce(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        // notificationId is a unique int for each notification that you must define
        if(progress<100) notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        else {
            acabaNotificacion();
            NOTIFICATION_ID++;
        }
    }

    void notificaYaDescargado(String song){
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_notification)
                .setContentTitle("Already downloaded: " + song)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        mBuilder.setOnlyAlertOnce(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        NOTIFICATION_ID++;
    }

    void notificaDescargaCancelada(){
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_notification)
                .setContentTitle("Download canceled: " + songName)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        mBuilder.setOnlyAlertOnce(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        NOTIFICATION_ID++;
    }

    void acabaNotificacion(){
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_notification)
                .setContentTitle("Downloaded: " + songName)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        mBuilder.setOnlyAlertOnce(true);
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, mBuilder.build());
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


    boolean doubleBackToExitPressedOnce = false;

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }
        this.doubleBackToExitPressedOnce = true;
        String message = "Click BACK again to exit the app (all remaining downloads will be removed) or Home to exit without close";
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                doubleBackToExitPressedOnce=false;
            }
        }, 2000);
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

    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (resultCode == RESULT_OK && requestCode == 1234){
            Uri treeUri = resultData.getData();
            sharedPreferences.edit().putString(SHARED_PREFS_NEW_PATH, treeUri.toString()).apply();
            String realPath = treeUri.getPath().replace("tree", "storage").replace(":", "/");
            if(!realPath.endsWith("/")) realPath+="/";
            socket.emit("newPath", realPath);
        }
    }

    public void copyFile(String inputPath, String inputFile, String outputPath, DocumentFile pickedDir) {

        InputStream in = null;
        OutputStream out = null;
        try {
            //create output directory if it doesn't exist
            File dir = new File(outputPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            in = new FileInputStream(inputPath + inputFile);
            //out = new FileOutputStream(outputPath + inputFile);

            DocumentFile file = null;
            if(outputPath.endsWith("mp3")) file = pickedDir.createFile("audio/mpeg3", outputPath);
            else file = pickedDir.createFile("audio/flac", outputPath);
            out = getContentResolver().openOutputStream(file.getUri());

            copyFile(in, out);
            in.close();


            // write the output file (You have now copied the file)
            out.flush();
            out.close();

            //scanNewSong(file.getUri());
        }
        catch (FileNotFoundException fnfe1) {
            /* I get the error here */
            Log.e("tag", fnfe1.getMessage());
        }
        catch (Exception e) {
            Log.e("tag", e.getMessage());
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
        mWebView.restoreState(savedInstanceState);
        //muestraPagina();
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
