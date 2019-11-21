package com.shuyi.basecamera;

import android.view.SurfaceHolder;

class CameraSurfaceCallback extends CameraPreviewCallback implements SurfaceHolder.Callback {

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(final SurfaceHolder holder, int format, final int width, final int height) {
        surfaceCreated(-1, holder, null, format, width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceDestroyed(holder, null);
    }
}
