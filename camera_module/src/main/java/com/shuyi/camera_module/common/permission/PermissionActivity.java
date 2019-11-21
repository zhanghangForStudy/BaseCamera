package com.shuyi.camera_module.common.permission;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.widget.FrameLayout;

import androidx.core.app.ActivityCompat;

public class PermissionActivity extends Activity implements ActivityCompat.OnRequestPermissionsResultCallback {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(new FrameLayout(this));
        String[] permissions = this.getIntent().getStringArrayExtra("permissions");
        String explain = this.getIntent().getStringExtra("explain");
        boolean ignoreRationaleCheck = this.getIntent().getBooleanExtra("ignoreCheck", false);
        if (permissions != null && permissions.length == 1 && permissions[0].equals("android.permission.SYSTEM_ALERT_WINDOW")) {
            Intent intent = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + this.getPackageName()));
            this.startActivityForResult(intent, 123);
        } else {
            this.requestCustomPermission(permissions, explain, ignoreRationaleCheck);
        }

    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 0) {
            PermissionUtil.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }

        this.finish();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 123) {
            PermissionUtil.onActivityResult(requestCode, resultCode, data);
        }

        this.finish();
    }

    public void finish() {
        super.finish();
        this.overridePendingTransition(0, 0);
    }

    private void requestCustomPermission(final String[] permissions, String explain, boolean ignoreRationaleCheck) {
        if ((ignoreRationaleCheck || this.shouldShowRequestPermissionRationale(permissions)) && !TextUtils.isEmpty(explain)) {
            final AlertDialog.Builder normalDialog =
                    new AlertDialog.Builder(getApplicationContext());
            normalDialog.setMessage(explain)
                    .setCancelable(true)
                    .setPositiveButton("确定",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    ActivityCompat.requestPermissions(PermissionActivity.this, permissions, 0);
                                }
                            }).show();
        } else {
            ActivityCompat.requestPermissions(this, permissions, 0);
        }

    }

    private boolean shouldShowRequestPermissionRationale(String[] permissions) {
        String[] var2 = permissions;
        int var3 = permissions.length;

        for (int var4 = 0; var4 < var3; ++var4) {
            String permission = var2[var4];
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }

        return false;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return true;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return true;
    }
}
