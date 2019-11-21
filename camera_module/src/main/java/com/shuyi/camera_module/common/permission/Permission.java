package com.shuyi.camera_module.common.permission;

import android.content.Context;

import androidx.collection.ArrayMap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

class Permission {
    private static final String READ_CONTACTS = "android.permission.READ_CONTACTS";
    private static final String CAMERA = "android.permission.CAMERA";
    private static final String WRITE_SETTINGS = "android.permission.WRITE_SETTINGS";
    private static final String ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION";
    private static final String ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION";
    private static final String VIBRATE = "android.permission.VIBRATE";
    private static final String SYSTEM_ALERT_WINDOW = "android.permission.SYSTEM_ALERT_WINDOW";
    private static final String RECORD_AUDIO = "android.permission.RECORD_AUDIO";
    private static final String WAKE_LOCK = "android.permission.WAKE_LOCK";
    public static final ArrayMap<String, String> sPermissionMapping = new ArrayMap();

    Permission() {
    }

    public static boolean isPermissionGranted(Context context, String[] permissions) {
        Object object = context.getSystemService("appops");

        try {
            Method method = object.getClass().getDeclaredMethod("checkOpNoThrow", Integer.TYPE, Integer.TYPE, String.class);
            method.setAccessible(true);
            String[] var4 = permissions;
            int var5 = permissions.length;

            for (int var6 = 0; var6 < var5; ++var6) {
                String permission = var4[var6];
                String internalPermission = (String) sPermissionMapping.get(permission);
                if (internalPermission != null) {
                    Field field = object.getClass().getDeclaredField(internalPermission);
                    field.setAccessible(true);
                    int op_key = (Integer) field.get(object);
                    int result = (Integer) method.invoke(object, op_key, context.getApplicationInfo().uid, context.getPackageName());
                    if (result != 0) {
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception var12) {
            return false;
        }
    }

    static {
        sPermissionMapping.put("android.permission.READ_CONTACTS", "OP_READ_CONTACTS");
        sPermissionMapping.put("android.permission.CAMERA", "OP_CAMERA");
        sPermissionMapping.put("android.permission.WRITE_SETTINGS", "OP_WRITE_SETTINGS");
        sPermissionMapping.put("android.permission.ACCESS_COARSE_LOCATION", "OP_COARSE_LOCATION");
        sPermissionMapping.put("android.permission.ACCESS_FINE_LOCATION", "OP_FINE_LOCATION");
        sPermissionMapping.put("android.permission.VIBRATE", "OP_VIBRATE");
        sPermissionMapping.put("android.permission.SYSTEM_ALERT_WINDOW", "OP_SYSTEM_ALERT_WINDOW");
        sPermissionMapping.put("android.permission.RECORD_AUDIO", "OP_RECORD_AUDIO");
        sPermissionMapping.put("android.permission.WAKE_LOCK", "OP_WAKE_LOCK");
    }
}
