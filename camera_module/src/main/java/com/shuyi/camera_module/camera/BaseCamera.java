package com.shuyi.camera_module.camera;

import android.Manifest;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;

import com.shuyi.camera_module.R;
import com.shuyi.camera_module.common.Callback;
import com.shuyi.camera_module.common.IConstants;
import com.shuyi.camera_module.common.permission.PermissionUtil;
import com.shuyi.camera_module.common.Result;
import com.shuyi.camera_module.common.ResultCode;
import com.shuyi.camera_module.common.Utils;

import java.io.File;
import java.util.List;

/**
 * 相机相关操作封装;
 * 主要包含了打开相机、开始预览、停止预览、关闭相机四个主要的操作;
 */
public class BaseCamera implements Camera.PreviewCallback, Camera.AutoFocusCallback {

    private static final String TAG = "BaseCamera";

    private static final int OPEN_CAMERA = 1;

    private static final int START_PREVIEW = 2;

    private static final int STOP_PREVIEW = 3;

    private static final int RELEASE_CAMERA = 4;

    private static final float PREVIEW_SIZE_RATIO_DELTA = 0.001f;
    private static final float PHOTO_RATIO = 0.75f;
    private static final float NORMAL_RATIO = 0.5625f;

    private Camera mCamera;


    /**
     * 相机工作线程
     */
    private HandlerThread mCameraThread;

    /**
     * 相机消息处理Handler对象
     */
    private Handler mCameraHandler;

    private byte[] mCachedPreviewBuffer;

    private Context mContext;

    /**
     * 该属性只会在camera线程中被读写;所以线程安全
     */
    private CameraData mCameraData;

    /**
     * 当前是否正在预览中;<br/>
     * 该值只会在camera线程中被读写;所以线程安全
     */
    private boolean mPreviewing = false;

    /**
     * 相机预览纹理，必须是全局的属性;<br>
     * 如果是方法内部的局部变量，则方法执行完成，纹理会随着局部变量的回收而被销毁;
     */
    private SurfaceTexture mSurfaceTexture;

    private boolean mCanUseAutoFocus;

    public BaseCamera(Context context) {
        mContext = context;
        mCameraThread = new HandlerThread("camera_worker");
        mCameraThread.start();
        mCameraHandler = new CameraHandler(mCameraThread.getLooper());
    }

    /**
     * 该方法只会在camera线程中被调用;所以线程安全
     *
     * @return
     */
    private boolean isOpened() {
        return mCamera != null
                && mCameraData != null
                && mCameraData.config != null
                && mCameraData.config instanceof OpenCameraConfig
                && mCameraThread != null
                && mCameraThread.isAlive()
                && mCameraHandler != null;
    }

    static boolean isCameraId(int id) {
        return id == Camera.CameraInfo.CAMERA_FACING_BACK
                || id == Camera.CameraInfo.CAMERA_FACING_FRONT;
    }

    static boolean isValidDisplayRotation(int displayRotation) {
        return displayRotation == 0
                || displayRotation == 90
                || displayRotation == 180
                || displayRotation == 270;
    }

    /**
     * 打开相机接口;<br/>
     * 多次调用该方法<b>不会多次</b>打开相机，除非调用{@link #releaseCamera(Callback)}方法<br/>
     * 简要流程:
     * <ol>
     * <li>请求权限</li>
     * <li>如果授权成功,则向{@link #mCameraThread}发送初始相机的操作;</li>
     * <li>配置相机参数{@link Camera.Parameters}</li>
     * <li>打开相机</li>
     * </ol>
     *
     * @param cameraConfig   相机配置;
     * @param resultCallback 打开相机异步回调接口;PS:执行该回调的线程不确定
     */
    public void openCamera(CameraConfig cameraConfig, Callback resultCallback) {
        try {
            if (cameraConfig == null) {
                Result.callbackResult(false, ResultCode.CAMERA_CONFIG_EMPTY_ERROR, resultCallback);
                return;
            }

            // 授权成功回调
            GrantedRunnable grantedRunnable = new GrantedRunnable((OpenCameraConfig) cameraConfig, resultCallback);
            // 授权失败回调
            DeniedRunnable deniedRunnable = new DeniedRunnable(resultCallback);
            requestCameraPermission(grantedRunnable, deniedRunnable);

        } catch (Throwable e) {
            Log.e(TAG, "failed to invoke openCamera method...", e);
        }
    }

    private void requestCameraPermission(final Runnable grantedRunnable, final Runnable deniedRunnable) {
        String requestCameraPermissionTip = mContext.getResources().getString(R.string.request_camera_permission_tip);
        String[] permissions =
                new String[]{Manifest.permission.CAMERA};
        PermissionUtil.buildPermissionTask(mContext, permissions)
                .setRationalStr(requestCameraPermissionTip)
                .setTaskOnPermissionGranted(new Runnable() {
                    @Override
                    public void run() {
                        if (grantedRunnable != null) {
                            grantedRunnable.run();
                        }
                    }
                })
                .setTaskOnPermissionDenied(new Runnable() {
                    @Override
                    public void run() {
                        if (deniedRunnable != null) {
                            deniedRunnable.run();
                        }
                    }
                })
                .execute();
    }

    // 在相机线程之中执行
    private void realOpenCamera(Message message) {
        Callback callback = null;
        Object[] objects;
        try {

            if (message == null
                    || message.what != OPEN_CAMERA
                    || !(message.obj instanceof Object[])
                    || (objects = (Object[]) message.obj) == null
                    || objects.length < 2
                    || Looper.myLooper() != mCameraThread.getLooper()) {
                // 此处分支无法拿到回调对象，所以直接返回
                Log.e(TAG, "because of bad params, open camera failed...");
                return;
            }

            if (objects[1] != null
                    && objects[1] instanceof Callback) {
                callback = (Callback) objects[1];
            }

            // 已经启动了
            if (isOpened()) {
                Result.callbackResult(true, callback);
                return;
            }

            // 检测相机配置对象
            if (objects[0] == null
                    || !(objects[0] instanceof OpenCameraConfig)) {
                Result.callbackResult(false, ResultCode.OPEN_CAMERA_PARAM_ERROR, callback);
                return;
            }

            OpenCameraConfig openCameraConfig = (OpenCameraConfig) objects[0];

            if (!isCameraId(openCameraConfig.cameraId)) {
                Result.callbackResult(false, ResultCode.CAMERA_ID_ERROR, callback);
                return;
            }

            mCameraData = new CameraData();
            mCameraData.config = openCameraConfig;
            openCameraConfig.isFrontCamera = openCameraConfig.cameraId == Camera.CameraInfo.CAMERA_FACING_FRONT;

            // 打开相机
            try {
                mCamera = Camera.open(openCameraConfig.cameraId);
                Log.e(TAG, "the camera object is " + mCamera == null ? "NULL" : mCamera.toString());
            } catch (Throwable throwable) {
                Log.e(TAG, "failed to open camera...", throwable);
                Result.callbackResult(false, ResultCode.OPEN_CAMERA_API_EXCEPTION, callback);
                return;
            }

            // 获取相机方向信息
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(openCameraConfig.cameraId, cameraInfo);
            openCameraConfig.cameraOrientation = cameraInfo.orientation;

            // 显示旋转角度
            if (!isValidDisplayRotation(openCameraConfig.displayOrientation)) {
                openCameraConfig.displayOrientation = IConstants.DEFAULT_DISPLAY_ROTATION;
            }
            mCamera.setDisplayOrientation(openCameraConfig.displayOrientation);
            if (openCameraConfig.displayOrientation == 90
                    || openCameraConfig.displayOrientation == 270) {
                int tmp = openCameraConfig.surfaceHeight;
                openCameraConfig.surfaceHeight = openCameraConfig.surfaceWidth;
                openCameraConfig.surfaceWidth = tmp;
            }

            // 设置相机参数
            setCameraParam(openCameraConfig);

            // 执行回调接口
            Result.callbackResult(true, callback);
        } catch (Throwable throwable) {
            Log.e(TAG, "open camera failed...", throwable);
            Result.callbackResult(false, ResultCode.OPEN_CAMERA_EXCEPTION, callback);
            // 关闭相机
            realReleaseCamera(mCameraHandler.obtainMessage(RELEASE_CAMERA));
        }
    }

    /**
     * 开启预览;<br/>
     * 该方法被执行之前，必须调用{@link #openCamera(CameraConfig, Callback)}方法;<br/>
     * 在调用{@link #openCamera(CameraConfig, Callback)}方法<br/>
     * 与调用{@link #releaseCamera(Callback)}方法之间,<br/>
     * <b>该方法的调用次数必须与{@link #stopPreview()}方法调用次数一致;</b><br/>
     * 主要的流程为:
     * <ol>
     * <li>设置预览surface holder或者预览纹理id</li>
     * <li>设置预览回调</li>
     * <li>开始预览</li>
     * <li>启动自动聚焦</li>
     * <ol/>
     *
     * @param surfaceHolder  相机预览的surface holder;
     * @param resultCallback 打开相机异步回调接口;PS:执行该回调的线程不确定
     */

    public void startPreview(SurfaceHolder surfaceHolder, Callback resultCallback) {
        startPreview(-1, null, surfaceHolder, resultCallback);
    }

    /**
     * 开启预览;<br/>
     * 该方法被执行之前，必须调用{@link #openCamera(CameraConfig, Callback)}方法;<br/>
     * 在调用{@link #openCamera(CameraConfig, Callback)}方法<br/>
     * 与调用{@link #releaseCamera(Callback)}方法之间,<br/>
     * <b>该方法的调用次数必须与{@link #stopPreview()}方法调用次数一致;</b><br/>
     * 主要的流程为:
     * <ol>
     * <li>设置预览surface holder或者预览纹理id</li>
     * <li>设置预览回调</li>
     * <li>开始预览</li>
     * <li>启动自动聚焦</li>
     * <ol/>
     *
     * @param surfaceTexture 相机预览的surface texture;
     * @param resultCallback 打开相机异步回调接口;PS:执行该回调的线程不确定
     */

    public void startPreview(SurfaceTexture surfaceTexture, Callback resultCallback) {
        startPreview(-1, surfaceTexture, null, resultCallback);
    }

    /**
     * 开启预览;<br/>
     * 该方法被执行之前，必须调用{@link #openCamera(CameraConfig, Callback)}方法;<br/>
     * 在调用{@link #openCamera(CameraConfig, Callback)}方法<br/>
     * 与调用{@link #releaseCamera(Callback)}方法之间,<br/>
     * <b>该方法的调用次数必须与{@link #stopPreview()}方法调用次数一致;</b><br/>
     * 主要的流程为:
     * <ol>
     * <li>设置预览surface holder或者预览纹理id</li>
     * <li>设置预览回调</li>
     * <li>开始预览</li>
     * <li>启动自动聚焦</li>
     * <ol/>
     *
     * @param textureId      相机预览的surface的纹理ID;
     * @param resultCallback 打开相机异步回调接口;PS:执行该回调的线程不确定
     */

    public void startPreview(int textureId, Callback resultCallback) {
        startPreview(textureId, null, null, resultCallback);
    }

    /**
     * 开启预览;<br/>
     * 该方法被执行之前，必须调用{@link #openCamera(CameraConfig, Callback)}方法;<br/>
     * 在调用{@link #openCamera(CameraConfig, Callback)}方法<br/>
     * 与调用{@link #releaseCamera(Callback)}方法之间,<br/>
     * <b>该方法的调用次数必须与{@link #stopPreview()}方法调用次数一致;</b><br/>
     * 主要的流程为:
     * <ol>
     * <li>设置预览surface holder或者预览纹理id</li>
     * <li>设置预览回调</li>
     * <li>开始预览</li>
     * <li>启动自动聚焦</li>
     * <ol/>
     *
     * @param textureId      相机预览的surface的纹理ID;textureId < 0 与 surfaceHolder == null不能同时成立;
     * @param surfaceHolder  相机预览的surface holder;textureId < 0 与 surfaceHolder == null不能同时成立;
     * @param resultCallback 打开相机异步回调接口;PS:执行该回调的线程不确定
     */

    private void startPreview(int textureId, SurfaceTexture surfaceTexture, SurfaceHolder surfaceHolder, Callback resultCallback) {
        try {
            if (textureId < 0
                    && surfaceHolder == null
                    && surfaceTexture == null) {
                Result.callbackResult(false, ResultCode.NO_PREVIEW_SURFACE_ERROR, resultCallback);
                return;
            }

            Object[] objects = new Object[3];
            objects[0] = surfaceHolder;
            objects[1] = resultCallback;
            objects[2] = surfaceTexture;
            Message message = mCameraHandler.obtainMessage(START_PREVIEW, objects);
            message.arg1 = textureId;
            message.sendToTarget();
        } catch (NullPointerException exception) {
            Result.callbackResult(false, ResultCode.PREVIEW_NULL_THREAD_ERROR, null, resultCallback);
        }
    }

    private void realStartPreview(Message message) {
        Callback callback = null;
        Object[] objects;
        try {
            // 验证message
            if (message == null
                    || message.what != START_PREVIEW
                    || !(message.obj instanceof Object[])
                    || (objects = (Object[]) message.obj) == null
                    || objects.length < 3
                    || Looper.myLooper() != mCameraThread.getLooper()) {
                // 此处分支无法拿到回调对象，所以直接返回
                Log.e(TAG, "because of bad params, submit preview failed...");
                return;
            }

            if (objects[1] != null
                    && objects[1] instanceof Callback) {
                callback = (Callback) objects[1];
            }

            // 未打开相机
            if (!isOpened()) {
                Result.callbackResult(false, ResultCode.START_PREVIEW_BEFORE_OPEN_CAMERA, callback);
                return;
            }

            // 已经打开预览了
            if (mPreviewing) {
                Result.callbackResult(true, callback);
                return;
            }

            if (message.arg1 < 0
                    && (objects[0] == null || !(objects[0] instanceof SurfaceHolder))
                    && (objects[2] == null || !(objects[2] instanceof SurfaceTexture))) {
                Result.callbackResult(false, ResultCode.PREVIEW_PARAM_ERROR, callback);
                return;
            }

            OpenCameraConfig openCameraConfig = (OpenCameraConfig) mCameraData.config;
            openCameraConfig.textureId = message.arg1;
            if (objects[0] != null
                    && objects[0] instanceof SurfaceHolder) {
                openCameraConfig.surfaceHolder = (SurfaceHolder) objects[0];
            }
            if (objects[2] != null
                    && objects[2] instanceof SurfaceTexture) {
                openCameraConfig.surfaceTexture = (SurfaceTexture) objects[2];
            }

            // 设置预览界面,优先使用纹理
            if (openCameraConfig.textureId >= 0) {
                mSurfaceTexture = new SurfaceTexture(openCameraConfig.textureId);
                mCamera.setPreviewTexture(mSurfaceTexture);
            } else if (openCameraConfig.surfaceTexture != null) {
                mSurfaceTexture = openCameraConfig.surfaceTexture;
                mCamera.setPreviewTexture(openCameraConfig.surfaceTexture);
            } else if (openCameraConfig.surfaceHolder != null) {
                mCamera.setPreviewDisplay(openCameraConfig.surfaceHolder);
            }

            // 设置预览回调
            mCachedPreviewBuffer = new byte[openCameraConfig.originPreviewWidth * openCameraConfig.originPreviewHeight * 3 / 2];
            mCamera.setPreviewCallbackWithBuffer(this);
            mCamera.addCallbackBuffer(mCachedPreviewBuffer);

            // 开始预览
            mCamera.startPreview();

            // 自动聚焦
            if (mCanUseAutoFocus) {
                // 先取消其他的自动聚焦操作
                mCamera.cancelAutoFocus();
                mCamera.autoFocus(this);
            }

            mPreviewing = true;

            Result.callbackResult(true, callback);
        } catch (Throwable throwable) {
            Log.e(TAG, "failed to invoke realStartPreview...", throwable);
            Result.callbackResult(false, ResultCode.START_PREVIEW_EXCEPTION, null, callback);
        }
    }

    private void setCameraParam(OpenCameraConfig openCameraConfig) {
        Camera.Parameters parameters = mCamera.getParameters();

        // 预览尺寸设置
        int[] defaultPreviewSize = OpenCameraConfig.getPreviewSizeByLevel(openCameraConfig.cameraPresetLevel);
        if (openCameraConfig.originPreviewWidth <= 0) {
            openCameraConfig.originPreviewWidth = defaultPreviewSize[0];
        }
        if (openCameraConfig.originPreviewHeight <= 0) {
            openCameraConfig.originPreviewHeight = defaultPreviewSize[1];
        }

        boolean isPhoto = openCameraConfig.cameraPresetLevel == CameraPresetLevel.PHOTO;
        tryToSetPreviewSize(parameters, openCameraConfig.originPreviewWidth, openCameraConfig.originPreviewHeight, isPhoto);

        Camera.Size previewSize = parameters.getPreviewSize();
        if (previewSize.width != openCameraConfig.originPreviewWidth) {
            openCameraConfig.originPreviewWidth = previewSize.width;
        }
        if (previewSize.height != openCameraConfig.originPreviewHeight) {
            openCameraConfig.originPreviewHeight = previewSize.height;
        }

        // 计算剪切后的预览帧图片
        computeCroppedPreviewSize(openCameraConfig);

        // 预览格式
        mCameraData.format = openCameraConfig.mPreviewFormat;
        List<Integer> supportedPreviewFormat = parameters.getSupportedPreviewFormats();
        boolean isSupported = false;
        for (Integer integer : supportedPreviewFormat) {
            if (integer == mCameraData.format) {
                isSupported = true;
                break;
            }
        }
        if (!isSupported) {
            mCameraData.format = IConstants.DEFAULT_PREVIEW_FORMAT;
        }
        parameters.setPreviewFormat(mCameraData.format);

        // 自动聚焦
        if (openCameraConfig.isAutoFocus) {
            mCanUseAutoFocus = tryToSetAutoFocus(openCameraConfig.autoFoucusMode, parameters);
        }

        mCamera.setParameters(parameters);
    }

    private boolean tryToSetAutoFocus(String focusMode, Camera.Parameters parameters) {
        List<String> cameraFocusModes = parameters.getSupportedFocusModes();
        // 优先使用外界传递而来的聚焦模式
        if (isSupportAutoFocus(focusMode)
                && cameraFocusModes.contains(focusMode)) {
            parameters.setFocusMode(focusMode);
            Log.i(TAG, "set focus mode: " + focusMode);
            return true;
        }
        if (cameraFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            Log.i(TAG, "set focus mode: Camera.Parameters.FOCUS_MODE_AUTO");
            return true;
        }

        if (cameraFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            // 部分机型无法聚焦
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            Log.i(TAG, "set focus mode: Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE");
            return true;
        }
        Log.i(TAG, "set focus mode: not supported.");
        return false;
    }

    private boolean isSupportAutoFocus(String focusMode) {
        return TextUtils.equals(focusMode, Camera.Parameters.FOCUS_MODE_AUTO)
                || TextUtils.equals(focusMode, Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)
                || TextUtils.equals(focusMode, Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)
                || TextUtils.equals(focusMode, Camera.Parameters.FOCUS_MODE_MACRO);

    }

    private void tryToSetPreviewSize(Camera.Parameters parameters, int width, int height, boolean isPhoto) {
        List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();

        // 指定的预览帧尺寸为第一候选尺寸
        for (Camera.Size size : previewSizes) {
            if (width == size.width && height == size.height) {
                parameters.setPreviewSize(width, height); // Need app to have an arbitration approach to select performance matched preview size.
                Log.i(TAG, "Camera preview size, width=" + width + ", height=" + height);
                return;
            }
        }

        // 第一个16:9或者4:3比例的尺寸为第二候选尺寸
        for (Camera.Size size : previewSizes) {
            if ((isPhoto && equalRatio(size, PHOTO_RATIO))
                    || equalRatio(size, NORMAL_RATIO)) {
                parameters.setPreviewSize(size.width, size.height); // Need app to have an arbitration approach to select performance matched preview size.
                Log.i(TAG, "Camera preview size, width=" + width + ", height=" + height);
                return;
            }
        }

        // 系统默认的预览尺寸为第三候选尺寸
        Camera.Size previewSize = parameters.getPreviewSize();
        Log.i(TAG, "Camera preview size, width=" + previewSize.width + ", height=" + previewSize.height);
    }

    private boolean equalRatio(Camera.Size size, float correctRatio) {
        if (size == null) {
            return false;
        }
        float widthAndHeightRatio = (float) size.width / (float) size.height;
        boolean deltaRatio = Math.abs(widthAndHeightRatio - correctRatio) < PREVIEW_SIZE_RATIO_DELTA;
        if (deltaRatio) {
            return true;
        }
        float heightAndWidthRatio = (float) size.height / (float) size.width;
        return Math.abs(heightAndWidthRatio - correctRatio) < PREVIEW_SIZE_RATIO_DELTA;
    }

    private void computeCroppedPreviewSize(OpenCameraConfig openCameraConfig) {
        if (openCameraConfig.isForbidCrop
                || openCameraConfig.surfaceWidth == 0
                || openCameraConfig.surfaceHeight == 0) {
            // 不剪切
            openCameraConfig.isCropped = false;
            openCameraConfig.previewWidth = openCameraConfig.originPreviewWidth;
            openCameraConfig.previewHeight = openCameraConfig.originPreviewHeight;
            Log.i(TAG, String.format("force no crop!!! the origin preview size[%d, %d]; the surface size[%d, %d]; the cropped size[%d, %d]",
                    openCameraConfig.originPreviewWidth, openCameraConfig.originPreviewHeight,
                    openCameraConfig.surfaceWidth, openCameraConfig.surfaceHeight,
                    openCameraConfig.previewWidth, openCameraConfig.previewHeight));
            return;
        }

        float surfaceRatio = (float) openCameraConfig.surfaceWidth / (float) openCameraConfig.surfaceHeight;
        float previewRatio = (float) openCameraConfig.originPreviewWidth / (float) openCameraConfig.originPreviewHeight;
        if (surfaceRatio >= previewRatio) {
            openCameraConfig.previewWidth = openCameraConfig.originPreviewWidth;
            openCameraConfig.previewHeight = (int) (openCameraConfig.originPreviewWidth / surfaceRatio);
        } else {
            openCameraConfig.previewWidth = (int) (openCameraConfig.originPreviewHeight * surfaceRatio);
            openCameraConfig.previewHeight = openCameraConfig.originPreviewHeight;
        }
        openCameraConfig.isCropped = openCameraConfig.previewWidth != openCameraConfig.originPreviewWidth;
        openCameraConfig.isCropped |= openCameraConfig.previewHeight != openCameraConfig.originPreviewHeight;
        if (openCameraConfig.isCropped) {
            // 避免预览尺寸的宽高是奇数的情况，以便裁剪
            // todo 玩美SDK底层需要要预览帧宽度必须与4对齐
            openCameraConfig.previewWidth = openCameraConfig.previewWidth / 4 * 4;
            openCameraConfig.previewHeight = openCameraConfig.previewHeight / 4 * 4;
        }
        Log.i(TAG, String.format("the origin preview size[%d, %d]; the surface size[%d, %d]; the cropped size[%d, %d]",
                openCameraConfig.originPreviewWidth, openCameraConfig.originPreviewHeight,
                openCameraConfig.surfaceWidth, openCameraConfig.surfaceHeight,
                openCameraConfig.previewWidth, openCameraConfig.previewHeight));
    }

    @Override
    public void onAutoFocus(boolean success, Camera camera) {
//        ALog.i("auto focus %b", success);
        try {
            mCamera.autoFocus(this);
        } catch (Throwable throwable) {
            Log.e(TAG, "some exception happened, when focusing auto...", throwable);
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        try {
            if (!isOpened()
                    || !mPreviewing) {
                return;
            }

            Bitmap bitmap = Utils.saveNV21ToBitmap(data, 1280, 720);
            Utils.saveBitmap(Bitmap.CompressFormat.JPEG, bitmap, new File("/sdcard/shuyi.jpg"));

            mCamera.addCallbackBuffer(mCachedPreviewBuffer);

            OpenCameraConfig cameraConfig = (OpenCameraConfig) mCameraData.config;
            if (cameraConfig.externalPreviewCallback != null) {
                // 进行裁剪
                if (cameraConfig.isCropped) {
                    int startX = (cameraConfig.originPreviewWidth - cameraConfig.previewWidth) / 2;
                    int startY = (cameraConfig.originPreviewHeight - cameraConfig.previewHeight) / 2;
                    // TODO: 2019/5/16 目前支持NV21格式的裁剪
                    // Utils.clipNV21方法平均在4毫秒左右(720P)
                    mCameraData.frameBuffer = Utils.clipNV21(data, cameraConfig.originPreviewWidth,
                            cameraConfig.originPreviewHeight, startX, startY,
                            cameraConfig.previewWidth, cameraConfig.previewHeight);
                } else {
                    // TODO: 2019/5/16 preview byte array同步的问题
                    mCameraData.frameBuffer = data;
                }

                cameraConfig.externalPreviewCallback.onPreviewCallback(mCameraData);
            }
        } catch (Throwable throwable) {
            Log.e(TAG, "failed to invoke preview callback...", throwable);
        }
    }

    public void stopPreview() {

        stopPreview(false, null);
    }

    private void stopPreview(boolean isNeedReleaseCamera, Callback callback) {
        try {
            Message message = mCameraHandler.obtainMessage(STOP_PREVIEW);
            message.arg1 = isNeedReleaseCamera ? 1 : 0;
            message.obj = callback;
            message.sendToTarget();
        } catch (Throwable throwable) {
            Log.e(TAG, "failed to invoke stopPreview method...", throwable);
        }
    }

    private void realStopPreview(Message message) {
        Callback callback = null;
        if (message.obj != null
                && message.obj instanceof Callback) {
            callback = (Callback) message.obj;
        }
        try {
            if (message == null
                    || message.what != STOP_PREVIEW
                    || Looper.myLooper() != mCameraThread.getLooper()) {
                return;
            }

            mPreviewing = false;

            if (mSurfaceTexture != null) {
                mSurfaceTexture.release();
                mSurfaceTexture = null;
            }

            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.setPreviewCallbackWithBuffer(null);
                if (mCameraData != null
                        && mCameraData.config != null
                        && mCameraData.config instanceof OpenCameraConfig) {
                    OpenCameraConfig openCameraConfig = (OpenCameraConfig) mCameraData.config;
                    if (mCanUseAutoFocus) {
                        mCamera.cancelAutoFocus();
                    }
                }
            }

            boolean releaseCamera = message.arg1 == 1 ? true : false;
            if (releaseCamera) {
                message = mCameraHandler.obtainMessage(RELEASE_CAMERA);
                message.obj = callback;
                realReleaseCamera(message);
            } else {
                Result.callbackResult(true, callback);
            }
        } catch (Throwable throwable) {
            Log.e(TAG, "failed to invoke stop preview...", throwable);
            Result.callbackResult(false, ResultCode.FAILURE_INTERNAL, callback);
        }
    }

    /**
     * 调用该方法后,相机线程将会停止;<br/>
     * 如果要继续使用，则需重新创建一个{@link BaseCamera}对象。
     */
    public void releaseCamera(Callback callback) {
        try {
            Message message = mCameraHandler.obtainMessage(RELEASE_CAMERA);
            message.obj = callback;
            message.sendToTarget();
        } catch (Throwable throwable) {
            Log.e(TAG, "failed to invoke releaseCamera method...", throwable);
        }
    }

    private void realReleaseCamera(Message message) {
        if (message == null
                || message.what != RELEASE_CAMERA
                || Looper.myLooper() != mCameraThread.getLooper()) {
            return;
        }
        Callback callback = null;
        if (message.obj != null
                && message.obj instanceof Callback) {
            callback = (Callback) message.obj;
        }
        try {
            if (mPreviewing) {
                // 如果还在预览，先停止预览
                stopPreview(true, callback);
                return;
            }
            if (mCameraHandler != null) {
                mCameraHandler.removeCallbacksAndMessages(null);
                mCameraHandler = null;
            }

            if (mCameraThread != null) {
                mCameraThread.quit();
                mCameraThread = null;
            }

            if (mCamera != null) {
                mCamera.setPreviewCallbackWithBuffer(null);
                mCamera.release();
                mCamera = null;
                mCameraData.config = null;
                mCameraData.frameBuffer = null;
            }

            mCameraData = null;
            mCachedPreviewBuffer = null;
            Result.callbackResult(true, callback);
        } catch (Throwable throwable) {
            Log.e(TAG, "failed to release camera...", throwable);
            Result.callbackResult(false, ResultCode.FAILURE_INTERNAL, callback);
        }
    }

    private class CameraHandler extends Handler {
        public CameraHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message message) {
            try {
                switch (message.what) {
                    case OPEN_CAMERA:
                        realOpenCamera(message);
                        break;
                    case START_PREVIEW:
                        realStartPreview(message);
                        break;
                    case STOP_PREVIEW:
                        realStopPreview(message);
                        break;
                    case RELEASE_CAMERA:
                        realReleaseCamera(message);
                        break;
                    default:
                        break;
                }
            } catch (Throwable throwable) {
                Log.e(TAG, "failed to deal with message...", throwable);
            }
        }
    }

    /**
     * 相机授权成功回调
     */
    private class GrantedRunnable implements Runnable {
        private OpenCameraConfig mOpenCameraConfig;
        private Callback mCallback;

        private GrantedRunnable(OpenCameraConfig opencameraConfig, Callback callback) {
            mOpenCameraConfig = opencameraConfig;
            mCallback = callback;
        }

        @Override
        public void run() {
            try {
                Object[] objects = new Object[2];
                objects[0] = mOpenCameraConfig;
                objects[1] = mCallback;
                Message message = mCameraHandler.obtainMessage(OPEN_CAMERA, objects);
                message.sendToTarget();
            } catch (NullPointerException exception) {
                Result.callbackResult(false, ResultCode.OPEN_CAMERA_NULL_THREAD_ERROR, mCallback);
            }
        }
    }

    /**
     * 相机授权失败回调
     */
    private class DeniedRunnable implements Runnable {
        private Callback mCallback;

        private DeniedRunnable(Callback callback) {
            mCallback = callback;
        }

        @Override
        public void run() {
            Result.callbackResult(false, ResultCode.CAMERA_PERMISSION_ERROR, mCallback);
        }
    }
}
