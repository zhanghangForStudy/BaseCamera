package com.shuyi.camera_module.camera;

import com.shuyi.camera_module.common.IConstants;

/**
 * 提供给外部模块使用的相机相关的数据;
 */
public class CameraData {

    /**
     * 相机相关的配置数据
     */
    public CameraConfig config;

    /**
     * 相机帧数据
     */
    public byte[] frameBuffer;

    /**
     * 预览帧格式;默认为{@link IConstants#DEFAULT_PREVIEW_FORMAT}
     */
    public int format = IConstants.DEFAULT_PREVIEW_FORMAT;
}
