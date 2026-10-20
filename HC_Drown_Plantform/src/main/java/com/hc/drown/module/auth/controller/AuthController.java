package com.hc.drown.module.auth.controller;

import com.hc.drown.common.api.Result;
import com.hc.drown.module.user.dto.AdminUserDetails;
import com.hc.drown.module.user.entity.SysPermission;
import com.hc.drown.module.user.entity.SysRole;
import com.hc.drown.module.user.entity.SysUser;
import com.hc.drown.module.user.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "认证管理", description = "用户登录认证相关接口")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService userService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody LoginParam loginParam) {
        String token = userService.login(loginParam.getUsername(), loginParam.getPassword());
        if (token == null) {
            return Result.validateFailed("用户名或密码错误");
        }
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("token", token);
        tokenMap.put("tokenHead", "Bearer ");
        return Result.success(tokenMap);
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterParam registerParam) {
        SysUser user = new SysUser();
        user.setUsername(registerParam.getUsername());
        user.setPassword(registerParam.getPassword());
        user.setGuardId(registerParam.getGuardId());
        user.setRealName(registerParam.getRealName());
        user.setPhone(registerParam.getPhone());

        SysUser registeredUser = userService.register(user, registerParam.getRoleCode());

        Map<String, Object> data = new HashMap<>();
        data.put("userId", registeredUser.getId());
        data.put("username", registeredUser.getUsername());
        data.put("guardId", registeredUser.getGuardId() != null ? registeredUser.getGuardId() : "");

        return Result.success(data, "注册成功");
    }

    @Data
    public static class RegisterParam {
        @NotEmpty(message = "用户名不能为空")
        private String username;
        @NotEmpty(message = "密码不能为空")
        private String password;
        private String guardId;
        @NotEmpty(message = "真实姓名不能为空")
        private String realName;
        private String phone;
        private String roleCode;
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/info")
    public Result<Map<String, Object>> getAdminInfo() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AdminUserDetails userDetails) {
            SysUser sysUser = userDetails.getSysUser();
            Map<String, Object> data = new HashMap<>();
            data.put("username", sysUser.getUsername());
            data.put("realName", sysUser.getRealName());
            data.put("avatar", sysUser.getAvatarUrl());
            data.put("phone", sysUser.getPhone());
            data.put("dutyStatus", sysUser.getDutyStatus());

            List<SysRole> roles = userService.getRoles(sysUser.getId());
            data.put("roles", roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList()));

            List<SysPermission> permissions = userService.getPermissions(sysUser.getId());
            data.put("permissions", permissions.stream().map(SysPermission::getPermCode).collect(Collectors.toList()));

            return Result.success(data);
        }
        return Result.unauthorized(null);
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success(null);
    }

    @Data
    public static class LoginParam {
        @NotEmpty(message = "用户名不能为空")
        private String username;
        @NotEmpty(message = "密码不能为空")
        private String password;
    }
}
