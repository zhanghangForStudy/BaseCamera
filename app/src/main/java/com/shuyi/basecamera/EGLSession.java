package com.shuyi.basecamera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.TextureView;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL;
import javax.microedition.khronos.opengles.GL10;

public class EGLSession implements TextureView.SurfaceTextureListener {
    private TextureView.SurfaceTextureListener mOuterSurfaceTextureListener;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private TextureView mTextureView;

    private EGL10 mEgl = null;
    private EGLDisplay mEGLDisplay = EGL10.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL10.EGL_NO_CONTEXT;
    private EGLConfig[] mEGLConfig = new EGLConfig[1];
    private EGLSurface mEglSurface;

    private int mCurrentSurfaceWidth;

    private int mCurrentSurfaceHeight;

    private static final int MSG_INIT_EGL_CONTEXT = 1;
    private static final int MSG_INIT_EGL_SURFACE = 2;
    private static final int MSG_CHANGE_SIZE = 3;
    private static final int MSG_RENDER = 4;
    private static final int MSG_DEINIT_SURFACE = 5;
    private static final int MSG_DEINIT_CONTEXT = 6;

    private boolean mIsInit = false;


    // 与渲染线程的生命周期基本保持一致
    // 第一次初始化的实际与渲染线程启动的时机不一致
    // 因为渲染线程启动强依赖视图的生命周期(onAttachedToWindow)
    // 如果onAttachedToWindow的时候再首次初始化该变量，那么getGPUImage方法将返回空
    // 因为getGPUImage方法会先于onAttachedToWindow方法被调用
    private GLSurfaceView.Renderer mRender;

    private GL mGL;

    private SurfaceTexture mCurrentSurfaceTexture;

    public EGLSession(TextureView textureView, Context context, GLSurfaceView.Renderer renderer) {
        mTextureView = textureView;
        mTextureView.setSurfaceTextureListener(this);
        mRender = renderer;
    }

    private void startRenderThreadIfNeed() {
        if (mHandlerThread == null
                || !mHandlerThread.isAlive()) {
            mHandlerThread = new HandlerThread("Renderer Thread");
            mHandlerThread.start();
            if (mHandler != null
                    && mHandler.getLooper() != mHandlerThread.getLooper()) {
                mHandler.sendEmptyMessage(MSG_DEINIT_SURFACE);
                mHandler.sendEmptyMessage(MSG_DEINIT_CONTEXT);
            }
            mHandler = new Handler(mHandlerThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case MSG_INIT_EGL_CONTEXT:
                            initEGLContext();
                            return;
                        case MSG_INIT_EGL_SURFACE:
                            initEGLSurface();
                            return;
                        case MSG_CHANGE_SIZE:
                            if (msg == null) {
                                return;
                            }
                            changeSurface(msg.arg1, msg.arg2);
                            return;
                        case MSG_RENDER:
                            drawFrame();
                            return;
                        case MSG_DEINIT_SURFACE:
                            destroyEGLSurface();
                            return;
                        case MSG_DEINIT_CONTEXT:
                            destroyEGLContext();
                            mCurrentSurfaceWidth = 0;
                            mCurrentSurfaceHeight = 0;
                            if (mHandlerThread != null) {
                                mHandlerThread.quit();
                                mHandlerThread = null;
                            }
                            if (mHandler != null) {
                                mHandler.removeCallbacksAndMessages(null);
                                mHandler = null;
                            }
                            return;
                        default:
                            return;
                    }
                }
            };
            mHandler.sendEmptyMessage(MSG_INIT_EGL_CONTEXT);
        }
    }

    private void initEGLContext() {
        mEgl = (EGL10) EGLContext.getEGL();

        //获取显示设备
        mEGLDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("eglGetDisplay failed! " + mEgl.eglGetError());
        }

        //version中存放EGL版本号
        int[] version = new int[2];

        //初始化EGL
        if (!mEgl.eglInitialize(mEGLDisplay, version)) {
            throw new RuntimeException("eglInitialize failed! " + mEgl.eglGetError());
        }

        //构造需要的配置列表
        int[] attributes = {
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_BUFFER_SIZE, 32,
                EGL10.EGL_RENDERABLE_TYPE, 4,
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
                EGL10.EGL_NONE
        };
        int[] configsNum = new int[1];

        //EGL选择配置
        if (!mEgl.eglChooseConfig(mEGLDisplay, attributes, mEGLConfig, 1, configsNum)) {
            throw new RuntimeException("eglChooseConfig failed! " + mEgl.eglGetError());
        }

        //创建上下文
        int[] contextAttribs = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL10.EGL_NONE
        };
        mEGLContext = mEgl.eglCreateContext(mEGLDisplay, mEGLConfig[0], EGL10.EGL_NO_CONTEXT, contextAttribs);

        if (mEGLDisplay == EGL10.EGL_NO_DISPLAY || mEGLContext == EGL10.EGL_NO_CONTEXT) {
            throw new RuntimeException("eglCreateContext fail failed! " + mEgl.eglGetError());
        }

        mGL = mEGLContext.getGL();
    }

    private void initEGLSurface() {
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        if (surfaceTexture == null)
            return;

        //创建EGL显示窗口
        mEglSurface = mEgl.eglCreateWindowSurface(mEGLDisplay, mEGLConfig[0], surfaceTexture, null);

        if (!mEgl.eglMakeCurrent(mEGLDisplay, mEglSurface, mEglSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed! " + mEgl.eglGetError());
        }

        if (!mIsInit) {
            if (mRender != null) {
                mRender.onSurfaceCreated((GL10) mGL, mEGLConfig[0]);
            }

            mIsInit = true;
        }
    }

    private void changeSurface(int width, int height) {
        width = width < 0 ? 0 : width;
        height = height < 0 ? 0 : height;
        if (mCurrentSurfaceWidth == width
                && mCurrentSurfaceHeight == height) {
            return;
        }
        mCurrentSurfaceWidth = width;
        mCurrentSurfaceHeight = height;
        if (mRender != null) {
            mRender.onSurfaceChanged((GL10) mGL, mCurrentSurfaceWidth, mCurrentSurfaceHeight);
        }
    }

    private void drawFrame() {
        if (mEGLConfig == null
                || mEglSurface == null
                || mEGLContext == null
                || !mIsInit) {
            nextFrame();
            return;
        }
        mEgl.eglMakeCurrent(mEGLDisplay, mEglSurface, mEglSurface, mEGLContext);
        GLES20.glViewport(0, 0, mCurrentSurfaceWidth, mCurrentSurfaceHeight);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glClearColor(1f, 1f, 0f, 0f);
        if (mRender != null) {
            mRender.onDrawFrame((GL10) mGL);
        }
        mEgl.eglSwapBuffers(mEGLDisplay, mEglSurface);
        nextFrame();
    }

    private void nextFrame() {
        if (mHandler != null) {
            mHandler.sendEmptyMessage(MSG_RENDER);
        }
    }

    private void destroyEGLSurface() {
        if (mEglSurface != null
                && mEGLDisplay != null) {
            mEgl.eglMakeCurrent(mEGLDisplay, EGL10.EGL_NO_SURFACE,
                    EGL10.EGL_NO_SURFACE,
                    EGL10.EGL_NO_CONTEXT);
            mEgl.eglDestroySurface(mEGLDisplay, mEglSurface);
            mEglSurface = null;
        }
        mIsInit = false;
    }

    private void destroyEGLContext() {
        if (mEGLContext != null
                && mEgl != null) {
            mEgl.eglDestroyContext(mEGLDisplay, mEGLContext);
            mEGLContext = null;
        }

        if (mEGLDisplay != null
                && mEgl != null) {
            mEgl.eglTerminate(mEGLDisplay);
            mEGLDisplay = null;
        }

        mEgl = null;

        mIsInit = false;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mCurrentSurfaceTexture = surface;
        dealSurfaceTextureAvailable(width, height);
    }

    private void dealSurfaceTextureAvailable(int width, int height) {
        if (width <= 0
                || height <= 0
                || mCurrentSurfaceTexture == null) {
            return;
        }
        if (mOuterSurfaceTextureListener != null) {
            mOuterSurfaceTextureListener.onSurfaceTextureAvailable(mCurrentSurfaceTexture, width, height);
        }

        // 确保渲染线程是活跃的;
        startRenderThreadIfNeed();

        // 更新surface
        mHandler.sendEmptyMessage(MSG_INIT_EGL_SURFACE);

        if (mOuterSurfaceTextureListener != null) {
            mOuterSurfaceTextureListener.onSurfaceTextureSizeChanged(mCurrentSurfaceTexture, width, height);
        }
        Message message = mHandler.obtainMessage(MSG_CHANGE_SIZE, width, height);
        message.sendToTarget();

        nextFrame();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        boolean result = dealSurfaceTextureDestroyed();
        mCurrentSurfaceTexture = null;
        return result;
    }

    private boolean dealSurfaceTextureDestroyed() {
        if (mCurrentSurfaceTexture == null) {
            return true;
        }
        if (mHandler != null) {
            Message message = mHandler.obtainMessage(MSG_DEINIT_SURFACE);
            message.sendToTarget();
        }
        if (mOuterSurfaceTextureListener != null) {
            return mOuterSurfaceTextureListener.onSurfaceTextureDestroyed(mCurrentSurfaceTexture);
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        if (mOuterSurfaceTextureListener != null) {
            mOuterSurfaceTextureListener.onSurfaceTextureUpdated(surface);
        }
    }

    public void destroy() {
        if (mHandler != null) {
            // 在销毁的时候，该方法会先于onSurfaceTextureDestroyed方法调用
            // 所以如果此刻应该销毁egl surface,
            // 必须先销毁egl surface，再销毁egl display;
            // 如果时序颠倒， 则elg surface会保留，造成内存泄漏;
            Message message = mHandler.obtainMessage(MSG_DEINIT_SURFACE);
            message.sendToTarget();
            message = mHandler.obtainMessage(MSG_DEINIT_CONTEXT);
            message.sendToTarget();
        }
    }
}
