package com.shuyi.camera_module.common;

import android.util.Log;

/**
 * 回调结果类
 *
 * @param <T>
 */
public class Result<T> {
    private static final String TAG = "Result";

    public final boolean SUCCESS;

    /**
     * 结果消息
     */
    public final ResultCode CODE;

    /**
     * 结果数据
     */
    public final T DATA;

    public static <D> void callbackResult(boolean isSuccess, Callback<D> callback) {
        callbackResult(isSuccess, isSuccess ? ResultCode.SUCCESS : null, null, callback);
    }

    public static <D> void callbackResult(boolean isSuccess, ResultCode code, Callback<D> callback) {
        callbackResult(isSuccess, code, null, callback);
    }

    public static <D> void callbackResult(boolean isSuccess, ResultCode code, D data, Callback<D> callback) {
        if (callback == null) {
            return;
        }
        try {
            Result<D> result = new Result<>(isSuccess, code, data);
            callback.callback(result);
        } catch (Throwable throwable) {
            Log.e(TAG, "failed to invoke the Callback...", throwable);
        }
    }

    private Result(boolean isSuccess, ResultCode resultCode, T data) {
        SUCCESS = isSuccess;
        CODE = resultCode == null ? ResultCode.FAILURE_UNKNOWN : resultCode;
        DATA = data;
    }

    @Override
    public String toString() {
        return "[" + SUCCESS + "," + CODE == null ? "" : CODE.name() + "]";
    }
}
