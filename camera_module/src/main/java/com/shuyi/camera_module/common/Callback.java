package com.shuyi.camera_module.common;

/**
 * 统一的、异步接口回调类
 *
 * @param <T> 结果数据的类型
 */
public interface Callback<T> {
    void callback(Result<T> result);
}
