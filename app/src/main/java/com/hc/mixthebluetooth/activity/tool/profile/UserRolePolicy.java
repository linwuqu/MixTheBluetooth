package com.hc.mixthebluetooth.activity.tool.profile;

import androidx.annotation.NonNull;

import com.hc.basiclibrary.viewBasic.HomeApplication;

/**
 * UserRolePolicy — 用户角色策略
 *
 * 作用：
 *   根据当前登录用户的角色，决定某些功能是否可用。
 *   目前只有一个判断：是否可以编辑设备参数（controlRatio / extractionTime 等）。
 *
 * 为什么需要角色策略？
 *   在某些场景下（如调试模式 vs 用户模式），只有管理员才能修改设备参数，
 *   普通用户只能查看，不能修改。CgmParameterDialog 用这个判断输入框是否可编辑。
 *
 * 当前策略：
 *   role == "admin" → canEditParameters() == true（参数可编辑）
 *   role != "admin" → canEditParameters() == false（参数只读）
 *
 * 依赖：
 *   CgmProfile.registerControls() 绑定 btnParams 时，把 rolePolicy 传给 CgmParameterDialog。
 *   CgmParameterDialog 用 rolePolicy.canEditParameters() 决定是否启用 EditText。
 */
public final class UserRolePolicy {

    /** 当前用户角色 */
    private final String role;

    public UserRolePolicy(@NonNull HomeApplication application) {
        // 从全局 Application 实例获取当前用户的角色标识
        this.role = application.getLimits();
    }

    /**
     * 判断当前用户是否有权限编辑设备参数。
     */
    public boolean canEditParameters() {
        return "admin".equals(role);
    }
}
