package com.shuyi.camera_module.common;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.FloatRange;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * 一些公共的工具静态方法
 */
public class Utils {
    private static final String TAG = "Utils";
    private static final int BITMAP_COMPRESS_QUALITY = 80;

    public static boolean isCollectionEmpty(Collection collection) {
        return collection == null || collection.size() <= 0;
    }

    public static boolean isCollectionEmpty(Map map) {
        return map == null || map.size() <= 0;
    }

    public static boolean isHttpUrl(String url) {
        // TODO: 2019-05-28 还需要做一次Host解析，避免不符合域名规则的字符串
        return !TextUtils.isEmpty(url) && (url.startsWith("http://") || url.startsWith("https://"));
    }

    public static boolean isMainThread() {
        return Looper.getMainLooper() == Looper.myLooper();
    }

    /**
     * NV21裁剪  算法效率 3ms
     *
     * @param src    源数据
     * @param width  源宽
     * @param height 源高
     * @param left   顶点坐标
     * @param top    顶点坐标
     * @param clip_w 裁剪后的宽
     * @param clip_h 裁剪后的高
     * @return 裁剪后的数据
     */
    public static byte[] clipNV21(byte[] src, int width, int height, int left, int top, int clip_w, int clip_h) {
        if (left > width || top > height) {
            return null;
        }
        //取偶
        int x = left / 2 * 2, y = top / 2 * 2;
        int w = clip_w / 2 * 2, h = clip_h / 2 * 2;
        int y_unit = w * h;
        int uv_unit = y_unit / 2;
        byte[] nData = new byte[y_unit + uv_unit];
        int ySrcPos = y * width;
        int yDestPos = 0;
        int uvSrcPos = width * height + (y * width) / 2;
        int uvDestPos = w * h;
        for (int i = y; i < y + h; i++) {
            //y内存块复制
            System.arraycopy(src, ySrcPos + x, nData, yDestPos, w);
            ySrcPos += width;
            yDestPos += w;
            //uv内存块复制
            if (((i - y) & 1) == 0) {
                System.arraycopy(src, uvSrcPos + x, nData, uvDestPos, w);
                uvSrcPos += width;
                uvDestPos += w;
            }
        }
        return nData;
    }

    /**
     * 保存bitmap到指定的File
     *
     * @param format 默认为{@link Bitmap.CompressFormat#PNG}
     * @param bitmap
     * @param file   指定保存bitmap的file，不允许为空，不允许不存在,不允许为文件夹;
     */
    public static boolean saveBitmap(Bitmap.CompressFormat format, Bitmap bitmap, File file) {
        if (bitmap == null
                || bitmap.isRecycled()
                || file == null) {
            return false;
        }
        if (!file.isFile()) {
            file.delete();
        }

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        boolean result = true;
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(file);
            if (format == null) {
                format = Bitmap.CompressFormat.PNG;
            }
            bitmap.compress(format, BITMAP_COMPRESS_QUALITY, fileOutputStream);
        } catch (Throwable throwable) {
            Log.e(TAG, String.format("failed to save bitmap to file[%s]", file.getAbsolutePath()), throwable);
            result = false;
        } finally {
            closeSafe(fileOutputStream);
        }
        return result;
    }

    public static Bitmap readBitmapFromRGBData(int width, int height, byte[] rgbData) {
        if (rgbData == null
                || rgbData.length <= 0
                || rgbData.length != width * height * 3) {
            return null;
        }
        try {
            int[] colors = new int[width * height];

            //Read in the pixels
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int start = y * width + x;
                    int r = rgbData[start * 3 + 0] >= 0 ? rgbData[start * 3 + 0] : rgbData[start * 3 + 0] + 255;
                    int g = rgbData[start * 3 + 1] >= 0 ? rgbData[start * 3 + 1] : rgbData[start * 3 + 1] + 255;
                    int b = rgbData[start * 3 + 2] >= 0 ? rgbData[start * 3 + 2] : rgbData[start * 3 + 2] + 255;
                    int color = Color.rgb(r, g, b);
                    colors[start] = color;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
            return bitmap;
        } catch (Throwable throwable) {
            Log.e(TAG, "failed to read bitmap from ppm file...");
        }
        return null;
    }

    public static Bitmap readGrayBitmapFromData(int width, int height, byte[] grayData) {
        if (grayData == null
                || grayData.length <= 0
                || grayData.length != width * height) {
            return null;
        }
        try {
            int[] colors = new int[width * height];

            //Read in the pixels
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int start = y * width + x;
                    byte byteGray = grayData[start];
                    int value = (int) (byteGray >= 0 ? byteGray : byteGray + 255);
                    colors[start] = value * 256 * 256 + value * 256 + value + 0xFF000000;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
            return bitmap;
        } catch (Throwable throwable) {
            Log.e(TAG, "failed to read bitmap from ppm file...");
        }
        return null;
    }

    public static byte convertFloatToColorByte(@FloatRange(from = 0, to = 1) float floatColor) {
        floatColor = floatColor < 0 ? 0 : (floatColor > 1 ? 1 : floatColor);
        floatColor = floatColor * 255;
        return (byte) (floatColor >= 0 ? floatColor : floatColor + 255);
    }

    public static int setCameraDisplayOrientation(Activity activity, int cameraId) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        // 一般而言，当前Activity为竖屏模式，degress为0；
        // 当前Activity为横屏模式,degress为90;
        int degrees;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
                degrees = 0;
                break;
        }

        // info.orientation表示预览帧图片为了与设备的自然方向对齐，所需的顺时针旋转的角度
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    /**
     * 将nv21保存为bitmap
     *
     * @param data
     * @param width
     * @param height
     * @return
     */
    public static Bitmap saveNV21ToBitmap(byte[] data, int width, int height) {
        if (data == null
                || data.length <= 0
                || width <= 0
                || height <= 0) {
            return null;
        }
        Bitmap bitmap = null;
        ByteArrayOutputStream stream = null;
        try {
            YuvImage image = new YuvImage(data, ImageFormat.NV21, width, height, null);
            stream = new ByteArrayOutputStream();
            image.compressToJpeg(new Rect(0, 0, width, height), 80, stream);
            bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
        } catch (Throwable throwable) {
            Log.e(TAG, "failed to save nv21 to bitmap...", throwable);
        } finally {
            closeSafe(stream);
        }
        return bitmap;
    }

    public static void closeSafe(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Throwable throwable) {
            // ignore throwable
        }
    }

    public static int covertDIP2Pixel(int size, Activity activity) {
        if (activity == null) {
            return size;
        }

        try {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            float density = displayMetrics.density;
            return (int) Math.floor(size * density);
        } catch (Throwable throwable) {
            Log.e(TAG, "failed to covertDIP2Pixel...");
        }
        return size;
    }

    public static Bitmap convertARGB8888ToRGB565(Bitmap bitmap) {
        if (bitmap != null
                && bitmap.getConfig() == Bitmap.Config.ARGB_8888) {
            Bitmap oldOne = bitmap;

            if (bitmap == null
                    && bitmap.isRecycled()) {
                return bitmap;
            }
            ByteArrayOutputStream byteArrayOutputStream = null;
            try {
                Bitmap convertedBitmap = Bitmap.createBitmap(bitmap.getWidth(),
                        bitmap.getHeight(),
                        Bitmap.Config.RGB_565);
                Canvas canvas = new Canvas(convertedBitmap);
                canvas.drawBitmap(bitmap, 0, 0, null);
                return convertedBitmap;
            } catch (Throwable throwable) {
                Log.e(TAG, "fialed to compress bitmap to a byte array...", throwable);
            } finally {
                Utils.closeSafe(byteArrayOutputStream);
            }

            if (bitmap == oldOne) {
                bitmap = oldOne;
            } else if (oldOne != null) {
                oldOne.recycle();
            }
        }
        return bitmap;
    }

    public static Bitmap cutBitmapWithSameWidthAndHeight(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int bitmapWidth = bitmap.getWidth();
        int bitmapHeight = bitmap.getHeight();
        if (bitmapWidth != bitmapHeight) {
            int cropSize = bitmapWidth > bitmapHeight ? bitmapHeight : bitmapWidth;
            int startX = (bitmapWidth - cropSize) / 2;
            int startY = (bitmapHeight - cropSize) / 2;
            Bitmap tmp = Bitmap.createBitmap(bitmap, startX, startY, cropSize, cropSize);
            bitmap.recycle();
            bitmap = tmp;
        }
        return bitmap;
    }
}
