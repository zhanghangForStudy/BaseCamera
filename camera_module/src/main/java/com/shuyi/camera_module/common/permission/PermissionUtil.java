package com.shuyi.camera_module.common.permission;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;

public final class PermissionUtil {
    private static PermissionRequestTask sCurrentPermissionRequestTask;

    public PermissionUtil() {
    }

    public static synchronized PermissionRequestTask buildPermissionTask(Context context, String[] permissions) {
        if (context == null) {
            throw new NullPointerException("context can not be null");
        } else if (permissions != null && permissions.length != 0) {
            PermissionRequestTask task = new PermissionRequestTask();
            if (context instanceof Application) {
                task.context = context;
            } else {
                task.context = context.getApplicationContext();
            }

            task.permissions = permissions;
            return task;
        } else {
            throw new NullPointerException("permissions can not be null");
        }
    }

    static void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (sCurrentPermissionRequestTask != null) {
            sCurrentPermissionRequestTask.onPermissionGranted(verifyPermissions(grantResults));
            sCurrentPermissionRequestTask = null;
        }

    }

    @TargetApi(23)
    static void onActivityResult(int requestCode, int resultCode, Intent data) {
        sCurrentPermissionRequestTask.onPermissionGranted(Settings.canDrawOverlays(sCurrentPermissionRequestTask.getContext()));
        sCurrentPermissionRequestTask = null;
    }

    private static boolean verifyPermissions(int[] grantResults) {
        if (grantResults.length < 1) {
            return false;
        } else {
            for (int i = 0; i < grantResults.length; ++i) {
                int result = grantResults[i];
                if (result != 0) {
                    return false;
                }
            }

            return true;
        }
    }

    public static class PermissionRequestTask {
        private Context context;
        private String[] permissions;
        private String explain;
        private Runnable permissionGrantedRunnable;
        private Runnable permissionDeniedRunnable;
        private boolean ignorePermissionRationaleCheck;

        public PermissionRequestTask() {
        }

        public Context getContext() {
            return this.context;
        }

        public PermissionRequestTask setRationalStr(String explain) {
            this.explain = explain;
            return this;
        }

        public PermissionRequestTask setTaskOnPermissionGranted(Runnable runnable) {
            if (runnable == null) {
                throw new NullPointerException("permissionGrantedRunnable is null");
            } else {
                this.permissionGrantedRunnable = runnable;
                return this;
            }
        }

        public PermissionRequestTask setTaskOnPermissionDenied(Runnable runnable) {
            this.permissionDeniedRunnable = runnable;
            return this;
        }

        public PermissionRequestTask setIgnorePermissionRationaleCheck(boolean ignore) {
            this.ignorePermissionRationaleCheck = ignore;
            return this;
        }

        public void execute() {
            if (VERSION.SDK_INT >= 23) {

                ArrayList<String> needGrantedPermissions = new ArrayList();

                for (int i = 0; i < permissions.length; ++i) {
                    String permission = permissions[i];
                    if (ActivityCompat.checkSelfPermission(this.context, permission) != 0) {
                        needGrantedPermissions.add(permission);
                    }
                }

                if (needGrantedPermissions.size() == 0) {
                    this.permissionGrantedRunnable.run();
                } else {
                    Intent intent = new Intent();
                    intent.setClass(this.context, PermissionActivity.class);
                    if (!(this.context instanceof Activity)) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }

                    intent.putExtra("permissions", this.permissions);
                    intent.putExtra("explain", this.explain);
                    intent.putExtra("ignoreCheck", this.ignorePermissionRationaleCheck);
                    PermissionUtil.sCurrentPermissionRequestTask = this;
                    this.context.startActivity(intent);
                }
            } else if (VERSION.SDK_INT >= 18) {
                if (Permission.isPermissionGranted(this.context, this.permissions)) {
                    this.permissionGrantedRunnable.run();
                } else {
                    this.permissionDeniedRunnable.run();
                }
            } else {
                this.permissionGrantedRunnable.run();
            }

        }

        void onPermissionGranted(boolean result) {
            if (result) {
                if (this.permissionGrantedRunnable != null) {
                    this.permissionGrantedRunnable.run();
                }
            } else if (this.permissionDeniedRunnable != null) {
                this.permissionDeniedRunnable.run();
            }

            this.destroy();
        }

        private void destroy() {
            this.context = null;
            this.permissionGrantedRunnable = null;
            this.permissionDeniedRunnable = null;
        }
    }
}
