package com.hc.drown.module.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hc.drown.module.user.entity.SysPermission;
import com.hc.drown.module.user.entity.SysRole;
import com.hc.drown.module.user.entity.SysUser;

import java.util.List;

public interface SysUserService extends IService<SysUser> {

    /**
     * 根据用户名获取后台管理员
     */
    SysUser getByUsername(String username);

    /**
     * 登录功能
     * @param username 用户名
     * @param password 密码
     * @return 生成的JWT token
     */
    String login(String username, String password);

    /**
     * 刷新token
     */
    String refreshToken(String oldToken);

    /**
     * 获取用户所有角色
     */
    List<SysRole> getRoles(Long userId);

    /**
     * 获取用户所有权限
     */
    List<SysPermission> getPermissions(Long userId);

    /**
     * 注册用户并绑定角色
     */
    SysUser register(SysUser sysUser, String roleCode);

    /**
     * 获取所有在岗且在线的救生员及其最新位置
     */
    List<SysUser> findActiveLifeguards();
}
