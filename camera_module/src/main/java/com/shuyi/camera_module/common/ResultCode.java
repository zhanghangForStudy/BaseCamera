package com.shuyi.camera_module.common;

/**
 * enum for result CODE of callback
 * Author:      yuanchang.llc
 * Created on:  2019/5/14 3:05 PM
 * Copyright (c) 2019 Alibaba. All rights reserved.
 */

public enum ResultCode {

    SUCCESS,                        // 返回调用成功


    /* 相机创建相关的错误码 */
    ADD_PREVIEW_VIEW_ERROR,           // 添加预览视图失败
    CAMERA_CONFIG_EMPTY_ERROR,        // 相机配置为空的错误
    OPEN_CAMERA_PARAM_ERROR,          // 打开相机参数错误
    START_PREVIEW_EXCEPTION,          // 开启预览时发生了异常
    NO_PREVIEW_SURFACE_ERROR,         // 没有预览的surface或者纹理错误
    OPEN_CAMERA_EXCEPTION,            // 开启相机时发生了异常
    OPEN_CAMERA_API_EXCEPTION,        // 开启相机的API发生了异常
    CAMERA_ID_ERROR,                  // 相机ID错误;
    START_PREVIEW_BEFORE_OPEN_CAMERA, // 打开相机前执行了预览操作
    PREVIEW_PARAM_ERROR,              // 预览参数错误
    OPEN_CAMERA_NULL_THREAD_ERROR,    // 打开相机时空线程错误
    PREVIEW_NULL_THREAD_ERROR,        // 开始预览是空线程错误
    CAMERA_PERMISSION_ERROR,          // 相机权限被拒绝错误

    FAILURE_UNKNOWN,                // 未知錯誤類型

    FAILURE_INTERNAL,                // 内部错误
}
