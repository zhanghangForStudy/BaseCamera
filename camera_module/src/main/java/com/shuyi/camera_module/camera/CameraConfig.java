package com.shuyi.camera_module.camera;

import android.graphics.SurfaceTexture;

import com.shuyi.camera_module.common.IConstants;

/**
 * 提供给外部模块使用的相机相关的参数;
 */
public class CameraConfig {

    /**
     * 预览帧的实时宽度
     */
    public int previewWidth;

    /**
     * 预览帧的实时高度
     */
    public int previewHeight;

    /**
     * 相机图像的方向;
     * 来源于{@link android.hardware.Camera.CameraInfo#orientation}
     */
    public int cameraOrientation = IConstants.DEFAULT_DISPLAY_ROTATION;

    /**
     * 是否是前置摄像头
     */
    public boolean isFrontCamera;

    /**
     * 相机预览帧对应的纹理ID
     */
    public int textureId = -1;


    /**
     * 相机预览帧对应的纹理
     */
    public SurfaceTexture surfaceTexture;
}
