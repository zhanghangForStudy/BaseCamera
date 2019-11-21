package com.shuyi.basecamera;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

public class MainActivity extends Activity {

    private SurfaceView mSurfaceView;

    private CameraPreviewCallback mCameraSurfaceCallback;

    private TextureView mTextureView;

    private EGLSession mEGLSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        mSurfaceView = new SurfaceView(this);
//        setContentView(mSurfaceView);
//        mCameraSurfaceCallback = new CameraSurfaceCallback();
//        mSurfaceView.getHolder().addCallback((SurfaceHolder.Callback) mCameraSurfaceCallback);

        // GL环境渲染yuv data数组
//        mSurfaceView = new GLSurfaceView(this);
//        setContentView(mSurfaceView);
//        GLSurfaceRender surfaceRender = new GLSurfaceRender(getApplicationContext());
//        ((GLSurfaceView) mSurfaceView).setEGLContextClientVersion(2);
//        ((GLSurfaceView) mSurfaceView).setRenderer(surfaceRender);
//        ((GLSurfaceView) mSurfaceView).setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
//        mCameraSurfaceCallback = new CameraSurfaceGLSurfaceCallback();
//        ((CameraSurfaceGLSurfaceCallback) mCameraSurfaceCallback).setGLSurfaceRender(surfaceRender);

        // GL环境渲染OES纹理
//        mSurfaceView = new GLSurfaceView(this);
//        setContentView(mSurfaceView);
//        GLSurfaceTextureRender surfaceRender = new GLSurfaceTextureRender(getApplicationContext());
//        surfaceRender.init(this);
//        ((GLSurfaceView) mSurfaceView).setEGLContextClientVersion(2);
//        ((GLSurfaceView) mSurfaceView).setRenderer(surfaceRender);
//        ((GLSurfaceView) mSurfaceView).setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // TextureView渲染OES纹理
        mTextureView = new TextureView(this);
        setContentView(mTextureView);
        GLSurfaceTextureRender surfaceTextureRender = new GLSurfaceTextureRender(getApplicationContext());
        surfaceTextureRender.init(this);
        mEGLSession = new EGLSession(mTextureView, getApplicationContext(), surfaceTextureRender);


        // 直接使用textureview预览
//        mTextureView = new TextureView(this);
//        setContentView(mTextureView);
//        mCameraSurfaceCallback = new CameraSurfaceTextureCallback();
//        mCameraSurfaceCallback.init(this);
//        mTextureView.setSurfaceTextureListener((TextureView.SurfaceTextureListener) mCameraSurfaceCallback);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCameraSurfaceCallback.releaseCamera();
        mCameraSurfaceCallback = null;
        if(mEGLSession!=null){
            mEGLSession.destroy();
            mEGLSession = null;
        }
    }
}
