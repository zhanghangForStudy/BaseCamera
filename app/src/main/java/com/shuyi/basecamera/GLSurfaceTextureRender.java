package com.shuyi.basecamera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLSurfaceTextureRender extends CameraPreviewCallback implements GLSurfaceView.Renderer {
    private static final float[] VERTEX_POSITIONS = {
            -1.0f, 1.0f, 0.0f,   // 左上角
            -1.0f, -1.0f, 0.0f,  // 左下角
            1.0f, -1.0f, 0.0f,   // 右下角
            -1.0f, 1.0f, 0.0f,   // 左上角
            1.0f, -1.0f, 0.0f,   // 右下角
            1.0f, 1.0f, 0.0f,    // 右上角

    };

    private static final float[] TEXTURE_POSITIONS = {
            .0f, 1.0f,   // 左上角
            .0f, .0f,    // 左下角
            1.0f, .0f,   // 右下角
            .0f, 1.0f,   // 左上角
            1.0f, .0f,    // 右下角
            1.0f, 1.0f   // 右上角
    };

    private float[] mProjectMatrix = new float[16];
    private float[] mViewMatrix = new float[16];
    private float[] mMVPMatrix = new float[16];
    private float[] mTransformMatrix = new float[16];

    private int mTextureId;

    private SurfaceTexture mSurfaceTexture;

    private int mProgram;

    private Context mContext;

    private int mVertexShaderId;

    private int mFragmentShaderId;

    private FloatBuffer mPositionBuffer;

    private FloatBuffer mTextureCoordinateBuffer;

    private FloatBuffer mMVPMatrixBuffer;

    private FloatBuffer mTextureTransformMatrixBuffer;

    private int mTextureHandler;

    private int mMVPMatrixHandler;

    private int mPositionHandler;

    private int mTextureCoordinateHandler;

    private int mTextureTransformMatrixHandler;

    /**
     * {@link SurfaceTexture#updateTexImage()}方法的原理
     */
    private long mTestTime;

    public GLSurfaceTextureRender(Context context) {
        mContext = context;
        mVertexShaderId = R.raw.camerapreview_vertex;
        mFragmentShaderId = R.raw.surfacetexture_fragment;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        final int vertexShader = GLUtils.createShader(GLES20.GL_VERTEX_SHADER, GLUtils.readStringFromRaw(mContext, mVertexShaderId));
        final int fragmentShader = GLUtils.createShader(GLES20.GL_FRAGMENT_SHADER, GLUtils.readStringFromRaw(mContext, mFragmentShaderId));
        mProgram = GLUtils.createProgram(vertexShader, fragmentShader);

        mPositionHandler = GLES20.glGetAttribLocation(mProgram, "a_position");
        mMVPMatrixHandler = GLES20.glGetUniformLocation(mProgram, "mvp_matrix");
        mTextureCoordinateHandler = GLES20.glGetAttribLocation(mProgram, "a_texture_coordinate");
        mTextureHandler = GLES20.glGetUniformLocation(mProgram, "textureOES");
        mTextureTransformMatrixHandler = GLES20.glGetUniformLocation(mProgram, "texture_transform_matrix");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        // 创建纹理及surface texture
        int[] textureIds = new int[]{-1};
        GLES20.glGenTextures(1, textureIds, 0);
        if (textureIds[0] <= 0) {
            throw new RuntimeException("could not generate the texture for the SurfaceTexture Obj...");
        }
        mTextureId = textureIds[0];
        mSurfaceTexture = new SurfaceTexture(mTextureId);

        // 打开相机
        surfaceCreated(-1, null, mSurfaceTexture, 0, width, height);

        // 设置视口
        GLES20.glViewport(0, 0, width, height);

        // 设置投影矩阵
        Matrix.frustumM(mProjectMatrix, 0, -1, 1, -1, 1, 1.0f, 2000f);

        // 设置视图矩阵
        Matrix.setIdentityM(mViewMatrix, 0);
//        Matrix.translateM(mViewMatrix, 0, 0, 0, 1.00001f);
        Matrix.translateM(mViewMatrix, 0, 0, 0, 1.1f);
        float[] tmp = new float[mViewMatrix.length];
        System.arraycopy(mViewMatrix, 0, tmp, 0, tmp.length);
        Matrix.invertM(mViewMatrix, 0, tmp, 0);

        // 设置MVP矩阵
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mViewMatrix, 0);

        // 填充buffer
        ByteBuffer byteBuffer = ByteBuffer
                .allocateDirect(mMVPMatrix.length * 4)
                .order(ByteOrder.nativeOrder());
        mMVPMatrixBuffer = byteBuffer.asFloatBuffer();
        mMVPMatrixBuffer.position(0);
        mMVPMatrixBuffer.put(mMVPMatrix);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // 1. 清空屏幕
        GLES20.glClearColor(0, 0, 1, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // 2. 使用程序
        GLES20.glUseProgram(mProgram);

        // 3. 更新纹理
        if (mTestTime == 0) {
            mTestTime = System.currentTimeMillis();
        } else if ((System.currentTimeMillis() - mTestTime) > 1) {
            // 每隔1毫秒更新一次纹理
            mSurfaceTexture.updateTexImage();
            mTestTime = System.currentTimeMillis();
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId);
        GLES20.glUniform1i(mTextureHandler, 0);

        // 4. 更新MVP矩阵
        mMVPMatrixBuffer.position(0);
        GLES20.glUniformMatrix4fv(mMVPMatrixHandler, 1, false, mMVPMatrixBuffer);

        // 5. 更新顶点坐标
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

        // 6. 更新纹理坐标
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

        // 7. 更新纹理transform矩阵
        mSurfaceTexture.getTransformMatrix(mTransformMatrix);
        if (mTextureTransformMatrixBuffer == null) {
            ByteBuffer tmp = ByteBuffer
                    .allocateDirect(mTransformMatrix.length * 4)
                    .order(ByteOrder.nativeOrder());
            mTextureTransformMatrixBuffer = tmp.asFloatBuffer();
        }
        mTextureTransformMatrixBuffer.position(0);
        mTextureTransformMatrixBuffer.put(mTransformMatrix);
        mTextureTransformMatrixBuffer.position(0);
        GLES20.glUniformMatrix4fv(mTextureTransformMatrixHandler, 1, false, mTextureTransformMatrixBuffer);

        // 8. 绘制
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, VERTEX_POSITIONS.length / 3);

        // 9. 收尾工作
        GLES20.glDisableVertexAttribArray(mPositionHandler);
        GLES20.glDisableVertexAttribArray(mTextureCoordinateHandler);
    }
}
