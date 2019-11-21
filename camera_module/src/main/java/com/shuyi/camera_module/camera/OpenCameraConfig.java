package com.shuyi.camera_module.camera;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.view.SurfaceHolder;

import com.shuyi.camera_module.common.IConstants;

class OpenCameraConfig extends CameraConfig {
    static final int HIGH_PREVIEW_WIDTH = 1920;
    static final int HIGH_PREVIEW_HEIGHT = 1080;
    static final int PHOTO_PREVIEW_WIDTH = 1280;
    static final int PHOTO_PREVIEW_HEIGHT = 960;
    static final int MEDIUM_PREVIEW_WIDTH = 1280;
    static final int MEDIUM_PREVIEW_HEIGHT = 720;

    OpenCameraConfig() {

    }

    /**
     * 自动聚焦的模式
     * 目前支持{@link Camera.Parameters#FOCUS_MODE_AUTO}、
     * {@link Camera.Parameters#FOCUS_MODE_CONTINUOUS_PICTURE}、
     * {@link Camera.Parameters#FOCUS_MODE_CONTINUOUS_VIDEO}、
     * {@link Camera.Parameters#FOCUS_MODE_MACRO}四种自动模式
     */
    String autoFoucusMode = Camera.Parameters.FOCUS_MODE_AUTO;

    /**
     * 预览格式
     */
    int mPreviewFormat = ImageFormat.NV21;

    /**
     * 初始时预览帧的宽度;
     * 如果预览帧尺寸的宽高比不等于SurfaceView的宽高比,则会对预览帧进行裁剪;
     * {@link #previewWidth}是裁剪后预览帧的宽度;
     * 如果该值小于等0，则默认使用{@link #PHOTO_PREVIEW_WIDTH}
     */
    int originPreviewWidth = -1;

    /**
     * 初始时预览帧的高度;
     * 如果预览帧尺寸的宽高比不等于SurfaceView的宽高比,则会对预览帧进行裁剪;
     * {@link #previewHeight}是裁剪后预览帧的高度;
     * 如果该值小于等0，则默认使用{@link #PHOTO_PREVIEW_HEIGHT}
     */
    int originPreviewHeight = -1;

    CameraPresetLevel cameraPresetLevel = CameraPresetLevel.MEDIUM;

    /**
     * 外部预览帧回调接口
     */
    IPreviewCallback externalPreviewCallback;

    /**
     * 相机预览的surface;<br/>
     * 要么该值不为空,要么{@link #textureId}大于等于0;<br/>
     * {@link #textureId}优先于该值;
     */
    SurfaceHolder surfaceHolder;

    /**
     * 当前surface的宽度;用以计算裁剪后的预览帧尺寸;
     */
    int surfaceWidth;

    /**
     * 当前surface的高度;用以计算裁剪后的预览帧尺寸;
     */
    int surfaceHeight;

    /**
     * 相机是否自动聚焦
     */
    boolean isAutoFocus = true;

    /**
     * 预览帧是否需要裁剪；<br/>
     * 该属性只作为内部标识使用，外部使用者不应该人为修改该值；<br/>
     * 如果要使用禁止裁剪功能，通过{@link #isForbidCrop}属性来设置<br/>
     */
    boolean isCropped;

    /**
     * 是否禁止裁剪;默认为不禁止裁剪
     */
    boolean isForbidCrop = false;

    /**
     * 显示界面旋转角度
     */
    int displayOrientation = IConstants.DEFAULT_DISPLAY_ROTATION;

    /**
     * 开启的相机ID;<br/>
     * 默认为{@link Camera.CameraInfo#CAMERA_FACING_BACK}
     */
    public int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

    static int[] getPreviewSizeByLevel(CameraPresetLevel cameraPresetLevel) {
        int[] result = new int[2];
        if (cameraPresetLevel == CameraPresetLevel.MEDIUM) {
            result[0] = MEDIUM_PREVIEW_WIDTH;
            result[1] = MEDIUM_PREVIEW_HEIGHT;
        } else if (cameraPresetLevel == CameraPresetLevel.HIGH) {
            result[0] = HIGH_PREVIEW_WIDTH;
            result[1] = HIGH_PREVIEW_HEIGHT;
        } else {
            result[0] = PHOTO_PREVIEW_WIDTH;
            result[1] = PHOTO_PREVIEW_HEIGHT;
        }
        return result;
    }
}
