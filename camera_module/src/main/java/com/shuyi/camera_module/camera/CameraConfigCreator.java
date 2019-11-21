package com.shuyi.camera_module.camera;

import android.hardware.Camera;

/**
 * 创建一个{@link OpenCameraConfig}对象;
 * <ul>
 * <li>
 * 调用{@link #setPreviewSize(int, int)}设置预览帧尺寸,
 * 默认预览帧尺寸为[{@link OpenCameraConfig#PHOTO_PREVIEW_WIDTH}、
 * {@link OpenCameraConfig#PHOTO_PREVIEW_HEIGHT}]；
 * </li>
 * <li>
 * 调用{@link #isAutoFocus(boolean)}设置是否自动聚焦,默认为true；
 * </li>
 * <li>
 * 调用{@link #setCameraId(int)}设置要启动的相机ID,默认为{@link Camera.CameraInfo#CAMERA_FACING_BACK};
 * </li>
 * <li>
 * 调用{@link #setDisplayRotation(int)}设置显示旋转角度,
 * 默认为90；
 * </li>
 * <li>
 * 调用{@link #setPreviewCallback(IPreviewCallback)}设置预览帧回调,
 * 默认为空;
 * </li>
 * <li>
 * 调用{@link #setSurfaceWidth(int)}设置surface的宽度
 * </li>
 * <li>
 * 调用{@link #setSurfaceHeight(int)}设置surface的高度
 * </li>
 * </ul>
 */
public class CameraConfigCreator {
    private OpenCameraConfig openCameraConfig = new OpenCameraConfig();

    /**
     * 该方法指定的预览帧尺寸，优先级最高(只要是相机支持的预览尺寸即可);
     *
     * @param previewWidth
     * @param previewHeight
     * @return
     */
    public CameraConfigCreator setPreviewSize(int previewWidth, int previewHeight) {
        openCameraConfig.originPreviewWidth = previewWidth;
        openCameraConfig.originPreviewHeight = previewHeight;
        return this;
    }

    public CameraConfigCreator setCameraPresetLevel(CameraPresetLevel cameraPresetLevel) {
        if (cameraPresetLevel == null) {
            return this;
        }
        openCameraConfig.cameraPresetLevel = cameraPresetLevel;
        return this;
    }

    public CameraConfigCreator isAutoFocus(boolean isAutoFocus) {
        openCameraConfig.isAutoFocus = isAutoFocus;
        return this;
    }

    public CameraConfigCreator setCameraId(int cameraId) {
        if (BaseCamera.isCameraId(cameraId)) {
            openCameraConfig.cameraId = cameraId;
        }
        return this;
    }

    public CameraConfigCreator setIsForbidCrop(boolean isForbid) {
        openCameraConfig.isForbidCrop = isForbid;
        return this;
    }

    public CameraConfigCreator setDisplayRotation(int displayRotation) {
        if (BaseCamera.isValidDisplayRotation(displayRotation)) {
            openCameraConfig.displayOrientation = displayRotation;
        }
        return this;
    }

    public CameraConfigCreator setPreviewCallback(IPreviewCallback previewCallback) {
        openCameraConfig.externalPreviewCallback = previewCallback;
        return this;
    }

    public CameraConfigCreator setSurfaceWidth(int surfaceWidth) {
        openCameraConfig.surfaceWidth = surfaceWidth;
        return this;
    }

    public CameraConfigCreator setSurfaceHeight(int surfaceHeight) {
        openCameraConfig.surfaceHeight = surfaceHeight;
        return this;
    }

    public CameraConfigCreator setAutoFocusMode(String focusMode) {
        openCameraConfig.autoFoucusMode = focusMode;
        return this;
    }

    // 因为涉及到裁剪，所以目前暂时只支持NV21
//    public CameraConfigCreator setPreviewFormat(int previewFormat) {
//        openCameraConfig.mPreviewFormat = previewFormat;
//        return this;
//    }

    public CameraConfig create() {
        return openCameraConfig;
    }
}
