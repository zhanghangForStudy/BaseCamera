package com.shuyi.basecamera;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLSurfaceRender implements GLSurfaceView.Renderer {
    private static final String TAG = GLSurfaceRender.class.getSimpleName();

    private static final float[] VERTEX_POSITIONS = {
            -1.0f, 1.0f, 4.0f,   // 左上角
            -1.0f, -1.0f, 4.0f,  // 左下角
            1.0f, -1.0f, 4.0f,   // 右下角
            -1.0f, 1.0f, 4.0f,   // 左上角
            1.0f, -1.0f, 4.0f,   // 右下角
            1.0f, 1.0f, 4.0f,    // 右上角

    };

//    private static final float[] TEXTURE_POSITIONS = {
//            .0f, 1.0f,   // 左上角
//            .0f, .0f,    // 左下角
//            1.0f, .0f,   // 右下角
//            .0f, 1.0f,   // 左上角
//            1.0f, .0f,    // 右下角
//            1.0f, 1.0f   // 右上角
//    };

    private static final float[] TEXTURE_POSITIONS = {
            .0f, 1.0f,   // 左上角
            1.0f, 1.0f,   // 右上角
            1.0f, .0f,    // 右下角
            .0f, 1.0f,   // 左上角
            1.0f, .0f,    // 右下角
            .0f, 0.0f,   // 左下角
    };

    private Context mContext;

    private int mProgram;

    private int mVertexShaderId;

    private int mFragmentShaderId;

    private int[] mYUVTextureId;

    private byte[] mYData;

    private byte[] mUVData;

    private ByteBuffer mYBuffer;

    private ByteBuffer mUVBuffer;

    private int mYUVWidth;

    private int mYUVHeight;

    private int mYTextureHandler;

    private int mUVTextureHandler;

    private int mMVPMatrixHandler;

    private int mPositionHandler;

    private FloatBuffer mPositionBuffer;

    private FloatBuffer mMVPMatrixBuffer;

    private int mTextureCoordinateHandler;

    private FloatBuffer mTextureCoordinateBuffer;
    private float[] mProjectMatrix = new float[16];
    private float[] mViewMatrix = new float[16];
    private float[] mMVPMatrix = new float[16];

    public GLSurfaceRender(Context context) {
        mContext = context;
        mVertexShaderId = R.raw.camerapreview_vertex;
        mFragmentShaderId = R.raw.yuv_fragment;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        final int vertexShader = GLUtils.createShader(GLES20.GL_VERTEX_SHADER, GLUtils.readStringFromRaw(mContext, mVertexShaderId));
        final int fragmentShader = GLUtils.createShader(GLES20.GL_FRAGMENT_SHADER, GLUtils.readStringFromRaw(mContext, mFragmentShaderId));
        mProgram = GLUtils.createProgram(vertexShader, fragmentShader);

        mYTextureHandler = GLES20.glGetUniformLocation(mProgram, "tex_y");
        mUVTextureHandler = GLES20.glGetUniformLocation(mProgram, "tex_uv");
        mPositionHandler = GLES20.glGetAttribLocation(mProgram, "a_position");
        mMVPMatrixHandler = GLES20.glGetUniformLocation(mProgram, "mvp_matrix");
        mTextureCoordinateHandler = GLES20.glGetAttribLocation(mProgram, "a_texture_coordinate");
    }


    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / height;
        Matrix.frustumM(mProjectMatrix, 0, -1, 1, -1, 1, 1f, 20f);

        Matrix.setIdentityM(mViewMatrix, 0);
        Matrix.translateM(mViewMatrix, 0, 0, 0, 5.1f);
        float[] tmp = new float[mViewMatrix.length];
        System.arraycopy(mViewMatrix, 0, tmp, 0, tmp.length);
        Matrix.invertM(mViewMatrix, 0, tmp, 0);

        Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mViewMatrix, 0);
        ByteBuffer byteBuffer = ByteBuffer
                .allocateDirect(mMVPMatrix.length * 4)
                .order(ByteOrder.nativeOrder());
        mMVPMatrixBuffer = byteBuffer.asFloatBuffer();
        mMVPMatrixBuffer.position(0);
        mMVPMatrixBuffer.put(mMVPMatrix);
    }

    public void updateYuvData(int width, int height, byte[] yuvData) {
        if (yuvData == null
                || width <= 0
                || height <= 0) {
            return;
        }
        synchronized (this) {
            if (width != mYUVWidth
                    || height != mYUVHeight
                    || mYData == null) {
                mYUVWidth = width;
                mYUVHeight = height;
                mYData = new byte[mYUVWidth * mYUVHeight];
                mYBuffer = ByteBuffer.allocateDirect(yuvData.length)
                        .order(ByteOrder.nativeOrder());

                mUVData = new byte[mYUVWidth * mYUVHeight / 2];
                mUVBuffer = ByteBuffer.allocate(yuvData.length)
                        .order(ByteOrder.nativeOrder());
            }

            System.arraycopy(yuvData, 0, mYData, 0, mYData.length);
            mYBuffer.position(0);
            mYBuffer.put(mYData);

            System.arraycopy(yuvData, mYData.length, mUVData, 0, mUVData.length);
            mUVBuffer.position(0);
            mUVBuffer.put(mUVData);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        try {
            // 1. 清空屏幕
            GLES20.glClearColor(0, 0, 1, 1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            // 2. 使用程序
            GLES20.glUseProgram(mProgram);

            // 3. 如果需要，创建yuv纹理
            createYuvTextureIfNeed();

            // 4. 更新yuv纹理
            synchronized (this) {
                if (mYData == null
                        || mUVData == null
                        || mYBuffer == null
                        || mUVData == null) {
                    return;
                }
                // 更新y纹理
                mYBuffer.position(0);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mYUVTextureId[0]);
                // GLES20.GL_LUMINANCE只按照亮度值存储
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,
                        0,
                        GLES20.GL_LUMINANCE,
                        mYUVWidth,
                        mYUVHeight,
                        0,
                        GLES20.GL_LUMINANCE,
                        GLES20.GL_UNSIGNED_BYTE,
                        mYBuffer);
                GLES20.glUniform1i(mYTextureHandler, 0);

                // 更新uv纹理
                mUVBuffer.position(0);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mYUVTextureId[1]);
                // GLES20.GL_LUMINANCE_ALPHA按照亮度值与透明度值存储
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,
                        0,
                        GLES20.GL_LUMINANCE_ALPHA,
                        mYUVWidth / 2,
                        mYUVHeight / 2,
                        0,
                        GLES20.GL_LUMINANCE_ALPHA,
                        GLES20.GL_UNSIGNED_BYTE,
                        mUVBuffer);
                GLES20.glUniform1i(mUVTextureHandler, 1);
            }

            // 5. 更新MVP矩阵
            mMVPMatrixBuffer.position(0);
            GLES20.glUniformMatrix4fv(mMVPMatrixHandler, 1, false, mMVPMatrixBuffer);

            // 6. 更新顶点坐标
            if (mPositionBuffer == null) {
                ByteBuffer tmp = ByteBuffer
                        .allocateDirect(VERTEX_POSITIONS.length * 4)
                        .order(ByteOrder.nativeOrder());
                mPositionBuffer = tmp.asFloatBuffer();
                mPositionBuffer.put(VERTEX_POSITIONS);
            }
            mPositionBuffer.position(0);
            GLES20.glEnableVertexAttribArray(mPositionHandler);
            GLES20.glVertexAttribPointer(mPositionHandler, 3, GLES20.GL_FLOAT, false, 0, mPositionBuffer);

            // 7. 更新纹理坐标
            if (mTextureCoordinateBuffer == null) {
                ByteBuffer tmp = ByteBuffer
                        .allocateDirect(TEXTURE_POSITIONS.length * 4)
                        .order(ByteOrder.nativeOrder());
                mTextureCoordinateBuffer = tmp.asFloatBuffer();
                mTextureCoordinateBuffer.put(TEXTURE_POSITIONS);
            }
            mTextureCoordinateBuffer.position(0);
            GLES20.glEnableVertexAttribArray(mTextureCoordinateHandler);
            GLES20.glVertexAttribPointer(mTextureCoordinateHandler, 2, GLES20.GL_FLOAT, false, 0, mTextureCoordinateBuffer);

            // 8. 绘制
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, VERTEX_POSITIONS.length / 3);

            // 9. 收尾工作
            GLES20.glDisableVertexAttribArray(mPositionHandler);
            GLES20.glDisableVertexAttribArray(mTextureCoordinateHandler);

        } catch (Throwable throwable) {
            Log.e(TAG, "some exceptions happened...", throwable);
        }
    }

    private void createYuvTextureIfNeed() {
        if (mYUVTextureId == null) {
            mYUVTextureId = new int[]{-1, -1};
            GLES20.glGenTextures(2, mYUVTextureId, 0);
            if (mYUVTextureId == null
                    || mYUVTextureId[0] == -1
                    || mYUVTextureId[1] == -1) {
                throw new RuntimeException("could not generate the yuv texture.");
            } else {
                for (int textureId : mYUVTextureId) {
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
                    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
                    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
                }
            }
        }
    }
}
