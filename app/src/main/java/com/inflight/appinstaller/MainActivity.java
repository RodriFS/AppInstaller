package com.inflight.appinstaller;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String PACKAGE_INSTALLED_ACTION =
            "com.example.android.apis.content.SESSION_API_PACKAGE_INSTALLED";
    private String INFO_TAG = "installerInfo";
    private String ERROR_TAG = "installerError";

    Button installButton;
    Button uninstallButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermissions();
        File[] apks = Environment.getExternalStoragePublicDirectory("Android/data/APKs").listFiles();
        int apkNumber = apks.length;

        PackageManager packageManager = getPackageManager();
        final String[] filePaths = new String[apkNumber];
        final String[] fileNames = new String[apkNumber];
        final String[] applicationIds = new String[apkNumber];
        for (int i = 0; i < apkNumber; i++) {
            filePaths[i] = apks[i].getAbsolutePath();
            fileNames[i] = apks[i].getName();
            applicationIds[i] = packageManager.getPackageArchiveInfo(filePaths[i], 0).packageName;
        }

        installButton = findViewById(R.id.install);
        uninstallButton = findViewById(R.id.uninstall);

        installButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < filePaths.length; i++) {
                    installPackage(filePaths[i], fileNames[i]);
                }
            }
        });

        uninstallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < applicationIds.length; i++) {
                    uninstallPackage(applicationIds[i]);
                }
            }
        });
    }


    private void installPackage(String filePath, String fileName) {
        PackageInstaller.Session session = null;
        try {
            PackageInstaller packageInstaller = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int sessionId = packageInstaller.createSession(params);
            session = packageInstaller.openSession(sessionId);

            addApkToInstallSession(filePath,fileName, session);

            // Create an install status receiver.
            Context context = getApplicationContext();
            Intent intent = new Intent(context, getClass());
            intent.setAction(PACKAGE_INSTALLED_ACTION);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
            IntentSender statusReceiver = pendingIntent.getIntentSender();
            // Commit the session (this will start the installation workflow).
            session.commit(statusReceiver);
            Log.i(INFO_TAG, fileName + " installed successfully");
        } catch (IOException e) {
            throw new RuntimeException("Couldn't install package", e);
        } catch (RuntimeException e) {
            if (session != null) {
                session.abandon();
            }
            throw e;
        }
    }

    private void uninstallPackage(String applicationId) {
        try {
            PackageInstaller packageInstaller = getPackageManager().getPackageInstaller();
            Context context = getApplicationContext();
            Intent intent = new Intent(context, getClass());
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
            IntentSender statusReceiver = pendingIntent.getIntentSender();
            packageInstaller.uninstall(applicationId, statusReceiver);
            Log.i(INFO_TAG, applicationId + " uninstalled successfully");
        } catch (Exception e) {
            throw new RuntimeException("Couldn't uninstall package", e);
        }

    }

    private boolean checkPermissions() {
        String[] permissions = new String[]{
                Manifest.permission.INSTALL_PACKAGES,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
        };
        int result;
        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String p : permissions) {
            result = ContextCompat.checkSelfPermission(this, p);
            if (result != PackageManager.PERMISSION_GRANTED) {
                Log.e(ERROR_TAG, "permission not granted for" + p);
                listPermissionsNeeded.add(p);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), 100);
            return false;
        }
        return true;
    }

    private void addApkToInstallSession(String filePath,String fileName, PackageInstaller.Session session)
            throws IOException {
        // It's recommended to pass the file size to openWrite(). Otherwise installation may fail
        // if the disk is almost full.
        try (OutputStream packageInSession = session.openWrite("temp_" + fileName, 0, -1);
             InputStream is = new FileInputStream(new File(filePath))) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = is.read(buffer)) >= 0) {
                packageInSession.write(buffer, 0, n);
            }
        }
    }
}
