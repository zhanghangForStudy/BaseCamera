package com.shuyi.basecamera;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.Toast;

import com.shuyi.camera_module.camera.BaseCamera;
import com.shuyi.camera_module.camera.CameraConfig;
import com.shuyi.camera_module.camera.CameraConfigCreator;
import com.shuyi.camera_module.common.Callback;
import com.shuyi.camera_module.common.Result;
import com.shuyi.camera_module.common.Utils;

class CameraPreviewCallback {
    private static final int CAMERA_ID = Camera.CameraInfo.CAMERA_FACING_BACK;
    private static final String TAG = CameraPreviewCallback.class.getSimpleName();
    private static final boolean DEBUG = false;
    private BaseCamera mBaseCamera;

    private Activity mActivity;

    private boolean mIsOpenCamera;

    private int mCurrentWidth;

    private int mCurrentHeight;

    private Surface mCurrentSurface;

    private Paint mPaint;

    private Handler mHandler;

//    private HandlerThread mDrawThread;

    // for test
    private Runnable mDrawCycleRunnable = new Runnable() {
        @Override
        public void run() {
            if (!DEBUG) {
                return;
            }
            synchronized (this) {
                if (mCurrentSurface == null) {
                    if (mHandler != null) {
                        mHandler.post(this);
                    }
                    return;
                }

                if (mPaint == null) {
                    mPaint = new Paint();
                    mPaint.setStyle(Paint.Style.FILL);
                    mPaint.setColor(Color.RED);
                }

                Canvas canvas = mCurrentSurface.lockCanvas(new Rect(0, 0, 800, 800));
                if (canvas != null) {
                    canvas.drawCircle(400, 400, 100, mPaint);
                    mCurrentSurface.unlockCanvasAndPost(canvas);
                }
            }

            if (mHandler != null) {
                mHandler.post(this);
            }
        }
    };

    void init(Activity activity) {
        mActivity = activity;
//        mDrawThread = new HandlerThread("draw_thread");
//        mDrawThread.start();
        if (DEBUG) {
            mHandler = new Handler(Looper.getMainLooper());
        }
    }

    void surfaceCreated(final int textureId, final SurfaceHolder holder, final SurfaceTexture surfaceTexture, int format, final int width, final int height) {
        if (holder == null
                && surfaceTexture == null
                && textureId < 0) {
            throw new IllegalArgumentException("no surface for preview...");
        }
        if (mBaseCamera == null) {
            mBaseCamera = new BaseCamera(mActivity);
        }

        if (mCurrentWidth != width
                || mCurrentHeight != height
                || !mIsOpenCamera) {
            if (mIsOpenCamera) {
                mBaseCamera.releaseCamera(new Callback() {
                    @Override
                    public void callback(Result result) {
                        openCamera(width, height, textureId, holder, surfaceTexture);
                    }
                });
            } else {
                openCamera(width, height, textureId, holder, surfaceTexture);
            }
            mIsOpenCamera = true;
            mCurrentWidth = width;
            mCurrentHeight = height;
        } else {
            startPreview(textureId, holder, surfaceTexture);
        }

        synchronized (this) {
            if (holder != null) {
                mCurrentSurface = holder.getSurface();
            } else if (surfaceTexture != null) {
                mCurrentSurface = new Surface(surfaceTexture);
            }
        }

        // 一边预览，一边画圆
        // 代码证明是不可行的；因为预览期间Surface.lockCanvas方法一直失败;
        if (DEBUG) {
            mHandler.postDelayed(mDrawCycleRunnable, 1000);
        }
    }

    CameraConfigCreator generateCameraConfig(int rotation, int width, int height) {
        CameraConfigCreator cameraConfigCreator = new CameraConfigCreator();
        return cameraConfigCreator.setCameraId(CAMERA_ID)
                .setDisplayRotation(rotation)
                .setSurfaceWidth(width)
                .setSurfaceHeight(height)
                .setAutoFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    }

    private void openCamera(int width, int height, final int textureId, final SurfaceHolder surfaceHolder, final SurfaceTexture surfaceTexture) {
        Log.e(TAG, "open camera...");
        int rotation = Utils.setCameraDisplayOrientation(mActivity, CAMERA_ID);
        CameraConfig cameraConfig = generateCameraConfig(rotation, width, height).create();
        mBaseCamera.openCamera(cameraConfig, new Callback() {
            @Override
            public void callback(Result result) {
                if (result != null
                        && result.SUCCESS) {
                    startPreview(textureId, surfaceHolder, surfaceTexture);
                } else {
                    Toast.makeText(mActivity, "打开相机失败:" + result.toString(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void startPreview(int textureId, SurfaceHolder surfaceHolder, SurfaceTexture surfaceTexture) {
        Log.e(TAG, "start preview...");
        if (surfaceHolder != null) {
            mBaseCamera.startPreview(surfaceHolder, new Callback() {
                @Override
                public void callback(Result result) {
                    Toast.makeText(mActivity, "预览结果:" + result.toString(), Toast.LENGTH_SHORT).show();
                }
            });
        } else if (surfaceTexture != null) {
            mBaseCamera.startPreview(surfaceTexture, new Callback() {
                @Override
                public void callback(Result result) {
                    Toast.makeText(mActivity, "预览结果:" + result.toString(), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            mBaseCamera.startPreview(textureId, new Callback() {
                @Override
                public void callback(Result result) {
                    Toast.makeText(mActivity, "预览结果:" + result.toString(), Toast.LENGTH_SHORT).show();
                }
            });
        }
//        mHandler.removeCallbacks(mDrawCycleRunnable);
    }


    void surfaceDestroyed(SurfaceHolder holder, SurfaceTexture surfaceTexture) {
        mBaseCamera.stopPreview();
    }

    void releaseCamera() {
        mBaseCamera.releaseCamera(null);
        if (DEBUG) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
//        mDrawThread.quit();
//        mDrawThread = null;
        mBaseCamera = null;
        mActivity = null;
    }
}
