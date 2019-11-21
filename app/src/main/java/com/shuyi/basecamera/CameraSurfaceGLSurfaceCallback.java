package com.shuyi.basecamera;

import android.view.SurfaceHolder;

import com.shuyi.camera_module.camera.CameraConfigCreator;
import com.shuyi.camera_module.camera.CameraData;
import com.shuyi.camera_module.camera.IPreviewCallback;

public class CameraSurfaceGLSurfaceCallback extends CameraPreviewCallback implements SurfaceHolder.Callback, IPreviewCallback {
    private GLSurfaceRender mGLSurfaceRender;

    public void setGLSurfaceRender(GLSurfaceRender glSurfaceRender) {
        mGLSurfaceRender = glSurfaceRender;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surfaceCreated(0, null, null, 0, width, height);
    }

    @Override
    CameraConfigCreator generateCameraConfig(int rotation, int width, int height) {
        CameraConfigCreator cameraConfigCreator = super.generateCameraConfig(rotation, width, height);
        cameraConfigCreator.setPreviewCallback(this);
        return cameraConfigCreator;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceDestroyed(null, null);
    }

    @Override
    public void onPreviewCallback(CameraData cameraData) {
        if (mGLSurfaceRender != null) {
            mGLSurfaceRender.updateYuvData(cameraData.config.previewWidth, cameraData.config.previewHeight, cameraData.frameBuffer);
        }
    }
}
