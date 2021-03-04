package com.mr.flutter.plugin.filepicker;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import droidninja.filepicker.FilePickerBuilder;
import droidninja.filepicker.FilePickerConst;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

public class FilePickerDelegate implements PluginRegistry.ActivityResultListener, PluginRegistry.RequestPermissionsResultListener {

    private static final String TAG = "FilePickerDelegate";
    private static final int REQUEST_CODE = (FilePickerPlugin.class.hashCode() + 43) & 0x0000ffff;

    private final Activity activity;
    private final PermissionManager permissionManager;
    private MethodChannel.Result pendingResult;
    private String type;
    private EventChannel.EventSink eventSink;

    private ArrayList<Uri> result = new ArrayList<>();


    public FilePickerDelegate(final Activity activity) {
        this(
                activity,
                null,
                new PermissionManager() {
                    @Override
                    public boolean isPermissionGranted(final String permissionName) {
                        return ActivityCompat.checkSelfPermission(activity, permissionName)
                                == PackageManager.PERMISSION_GRANTED;
                    }

                    @Override
                    public void askForPermission(final String permissionName, final int requestCode) {
                        ActivityCompat.requestPermissions(activity, new String[]{permissionName}, requestCode);
                    }

                }
        );
    }

    public void setEventHandler(final EventChannel.EventSink eventSink) {
        this.eventSink = eventSink;
    }

    @VisibleForTesting
    FilePickerDelegate(final Activity activity, final MethodChannel.Result result, final PermissionManager permissionManager) {
        this.activity = activity;
        this.pendingResult = result;
        this.permissionManager = permissionManager;
    }


    @Override
    public boolean onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        result.clear();
        if(type == null) {
            return false;
        }

        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {

            if (eventSink != null) {
                eventSink.success(true);
            }

            if (type.equals("media")) {
                result.addAll(data.<Uri>getParcelableArrayListExtra(FilePickerConst.KEY_SELECTED_MEDIA));
            } else {
                result.addAll(data.<Uri>getParcelableArrayListExtra(FilePickerConst.KEY_SELECTED_DOCS));
            }
            List<FileInfo> returnList = new ArrayList<>();

            for (Uri uri : result) {
                if (uri.getPath() != null) {
                    FileInfo fileInfo = FileUtils.getFileInfo(activity, uri);
                    if (fileInfo != null) {
                        returnList.add(fileInfo);
                    }
                }
            }

            finishWithSuccess(returnList);
            return true;

        } else if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_CANCELED) {
            Log.i(TAG, "User cancelled the picker request");
            finishWithSuccess(null);
            return true;
        } else if (requestCode == REQUEST_CODE) {
            finishWithError("unknown_activity", "Unknown activity error, please fill an issue.");
        }
        return false;
    }

    @Override
    public boolean onRequestPermissionsResult(final int requestCode, final String[] permissions, final int[] grantResults) {

        if (REQUEST_CODE != requestCode) {
            return false;
        }

        final boolean permissionGranted =
                grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        if (permissionGranted) {
            this.startFileExplorer();
        } else {
            finishWithError("read_external_storage_denied", "User did not allowed reading external storage");
        }

        return true;
    }

    private boolean setPendingMethodCallAndResult(final MethodChannel.Result result) {
        if (this.pendingResult != null) {
            return false;
        }
        this.pendingResult = result;
        return true;
    }

    private static void finishWithAlreadyActiveError(final MethodChannel.Result result) {
        result.error("already_active", "File picker is already active", null);
    }

    @SuppressWarnings("deprecation")
    private void startFileExplorer() {
        if (type.equals("media")) {
            FilePickerBuilder.getInstance()
                    .setMaxCount(500) //optional
                    .setActivityTheme(R.style.LibAppTheme) //opt
                    .enableVideoPicker(true)
                    .enableCameraSupport(false)
                    .pickPhoto(activity, REQUEST_CODE);
        } else {
            FilePickerBuilder.getInstance()
                    .setMaxCount(500) //optional
                    .setActivityTheme(R.style.LibAppTheme) //opt
                    .enableVideoPicker(true)
                    .pickFile(activity, REQUEST_CODE);
        }
    }

    @SuppressWarnings("deprecation")
    public void startFileExplorer(final String type, final boolean isMultipleSelection, final boolean withData, final String[] allowedExtensions, final MethodChannel.Result result) {

        if (!this.setPendingMethodCallAndResult(result)) {
            finishWithAlreadyActiveError(result);
            return;
        }

        this.type = type;

        if (!this.permissionManager.isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            this.permissionManager.askForPermission(Manifest.permission.READ_EXTERNAL_STORAGE, REQUEST_CODE);
            return;
        }

        this.startFileExplorer();
    }

    @SuppressWarnings("unchecked")
    private void finishWithSuccess(FileInfo data, MethodChannel.Result result) {
        if (result != null) {
            result.success(data.path);
        }
    }

    @SuppressWarnings("unchecked")
    private void finishWithSuccess(Object data) {
        if (eventSink != null) {
            this.dispatchEventStatus(false);
        }

        // Temporary fix, remove this null-check after Flutter Engine 1.14 has landed on stable
        if (this.pendingResult != null) {

            if(data != null && !(data instanceof String)) {
                final ArrayList<HashMap<String, Object>> files = new ArrayList<>();

                for (FileInfo file : (ArrayList<FileInfo>)data) {
                    files.add(file.toMap());
                }
                data = files;
            }

            this.pendingResult.success(data);
            this.clearPendingResult();
        }
    }

    private void finishWithError(final String errorCode, final String errorMessage, MethodChannel.Result result) {
        if (result == null) {
            return;
        }

        result.error(errorCode, errorMessage, null);
    }

    private void finishWithError(final String errorCode, final String errorMessage) {
        if (this.pendingResult == null) {
            return;
        }

        if (eventSink != null) {
            this.dispatchEventStatus(false);
        }
        this.pendingResult.error(errorCode, errorMessage, null);
        this.clearPendingResult();
    }

    private void dispatchEventStatus(final boolean status) {
        new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(final Message message) {
                eventSink.success(status);
            }
        }.obtainMessage().sendToTarget();
    }


    private void clearPendingResult() {
        this.pendingResult = null;
    }

    public void getFilePath(final String uri, final MethodChannel.Result result) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final FileInfo file = FileUtils.openFileStream(FilePickerDelegate.this.activity, Uri.parse(uri), false);
                if (file != null) {
                    finishWithSuccess(file, result);
                } else {
                    finishWithError("parse uri fail ", uri, result);
                }

            }
        }).start();
    }

    interface PermissionManager {
        boolean isPermissionGranted(String permissionName);

        void askForPermission(String permissionName, int requestCode);
    }

}
