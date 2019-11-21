package com.shuyi.basecamera;

import android.content.Context;
import android.opengl.GLES20;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class GLUtils {
    static String readStringFromRaw(Context context, int resourceId) {
        final InputStream in = context.getResources().openRawResource(resourceId);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        int len;
        try {
            while (-1 != (len = in.read(buffer))) {
                out.write(buffer, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return new String(out.toByteArray());
    }

    static int createShader(int type, String code) {
        final int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        final int[] sillyReturns = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, sillyReturns, 0);
        if (GLES20.GL_FALSE == sillyReturns[0]) {
            final String shaderType = type == GLES20.GL_VERTEX_SHADER ? "VertexShader" : "FragmentShader";
            throw new RuntimeException(shaderType + " create failed: " + GLES20.glGetShaderInfoLog(shader));
        }
        return shader;
    }

    static int createProgram(int vertexShader, int fragmentShader) {
        final int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        final int[] sillyReturns = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, sillyReturns, 0);
        if (GLES20.GL_FALSE == sillyReturns[0]) {
            throw new RuntimeException("Program link failed:" + GLES20.glGetProgramInfoLog(program));
        }

        return program;
    }
}
