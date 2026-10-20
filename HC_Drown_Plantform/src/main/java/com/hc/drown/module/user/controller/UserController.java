package com.hc.drown.module.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hc.drown.common.api.Result;
import com.hc.drown.module.user.entity.SysUser;
import com.hc.drown.module.user.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Tag(name = "用户管理", description = "后台用户/救生员的CRUD管理")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final SysUserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final com.hc.drown.module.task.service.RescueTaskService rescueTaskService;

    @Operation(summary = "分页获取用户列表")
    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public Result<Page<SysUser>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                      @RequestParam(defaultValue = "10") Integer pageSize,
                                      @RequestParam(required = false) String keyword) {
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        Page<SysUser> userPage = userService.page(page, wrapper);
        for (SysUser user : userPage.getRecords()) {
            // Query roleCode
            List<String> roles = jdbcTemplate.queryForList(
                    "SELECT r.role_code FROM sys_role r JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = ?",
                    String.class,
                    user.getId()
            );
            if (!roles.isEmpty()) {
                user.setRoleCode(roles.get(0));
            }
            // Query associated deviceIds
            List<Long> devices = jdbcTemplate.queryForList(
                    "SELECT device_id FROM sys_user_device WHERE user_id = ?",
                    Long.class,
                    user.getId()
            );
            user.setDeviceIds(devices);

            // Query latest WKT location reported via mobile GPS API (/api/v1/tasks/location)
            List<String> locations = jdbcTemplate.queryForList(
                    "SELECT location FROM lifeguard_location WHERE user_id = ? ORDER BY recorded_at DESC LIMIT 1",
                    String.class,
                    user.getId()
            );
            if (!locations.isEmpty()) {
                user.setLastWktLocation(locations.get(0));
            }
        }
        return Result.success(userPage);
    }

    @Operation(summary = "新增用户")
    @PostMapping
    @PreAuthorize("hasAuthority('user:write')")
    @Transactional
    public Result<SysUser> create(@RequestBody SysUser user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleted(0);
        userService.save(user);

        // Bind roleCode
        if (user.getRoleCode() != null && !user.getRoleCode().isEmpty()) {
            List<Long> roleIds = jdbcTemplate.queryForList(
                    "SELECT id FROM sys_role WHERE role_code = ? AND deleted = 0",
                    Long.class,
                    user.getRoleCode()
            );
            if (!roleIds.isEmpty()) {
                jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", user.getId(), roleIds.get(0));
            }
        }

        // Bind deviceIds
        if (user.getDeviceIds() != null && !user.getDeviceIds().isEmpty()) {
            for (Long devId : user.getDeviceIds()) {
                jdbcTemplate.update("INSERT INTO sys_user_device (user_id, device_id) VALUES (?, ?)", user.getId(), devId);
            }
        }

        return Result.success(user);
    }

    @Operation(summary = "修改用户基本信息")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:write')")
    @Transactional
    public Result<Boolean> update(@PathVariable Long id, @RequestBody SysUser user) {
        user.setId(id);
        user.setUpdatedAt(LocalDateTime.now());
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null); // 不修改密码
        }
        boolean success = userService.updateById(user);

        if (success) {
            // Update roleCode
            if (user.getRoleCode() != null && !user.getRoleCode().isEmpty()) {
                jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", id);
                List<Long> roleIds = jdbcTemplate.queryForList(
                        "SELECT id FROM sys_role WHERE role_code = ? AND deleted = 0",
                        Long.class,
                        user.getRoleCode()
                );
                if (!roleIds.isEmpty()) {
                    jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", id, roleIds.get(0));
                }
            }

            // Update deviceIds
            jdbcTemplate.update("DELETE FROM sys_user_device WHERE user_id = ?", id);
            if (user.getDeviceIds() != null && !user.getDeviceIds().isEmpty()) {
                for (Long devId : user.getDeviceIds()) {
                    jdbcTemplate.update("INSERT INTO sys_user_device (user_id, device_id) VALUES (?, ?)", id, devId);
                }
            }
        }

        return Result.success(success);
    }

    @Operation(summary = "逻辑删除用户")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('user:delete', 'user:write', 'ROLE_ADMIN')")
    @Transactional
    public Result<Boolean> delete(@PathVariable Long id) {
        int rows = jdbcTemplate.update("UPDATE sys_user SET deleted = 1, updated_at = NOW() WHERE id = ?", id);
        return Result.success(rows > 0);
    }

    @Operation(summary = "救生员上报在岗状态 (UC12)")
    @PutMapping("/duty-status")
    @PreAuthorize("hasAuthority('task:update')")
    public Result<Boolean> updateDutyStatus(@RequestParam Integer dutyStatus) {
        // 获取当前登录用户
        org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.hc.drown.module.user.dto.AdminUserDetails userDetails) {
            SysUser user = userDetails.getSysUser();
            user.setDutyStatus(dutyStatus);
            user.setUpdatedAt(LocalDateTime.now());
            boolean success = userService.updateById(user);
            return Result.success(success);
        }
        return Result.failed("无法获取当前登录救生员");
    }

    @Operation(summary = "获取当前在岗/活跃救生员最新位置列表")
    @GetMapping("/active-locations")
    public Result<List<SysUser>> getActiveLocations() {
        return Result.success(userService.findActiveLifeguards());
    }

    @Operation(summary = "预留救生员/人物通知推送接口 (预留接口)")
    @PostMapping("/notify")
    public Result<Boolean> notifyRescuer(@RequestBody NotifyParam param) {
        // 预留接口：用于后续向特定救生员/人物推送安全预警通知或协同指令
        log.info("预留接口被调用：推送通知给用户 ID={}, 内容: {}", param.getUserId(), param.getMessage());
        return Result.success(true);
    }

    @Operation(summary = "后台模拟上报指定救生员的位置")
    @PostMapping("/{id}/location")
    @PreAuthorize("hasAuthority('user:write')")
    @Transactional
    public Result<Boolean> reportLifeguardLocation(@PathVariable Long id,
                                                   @RequestParam Double lng,
                                                   @RequestParam Double lat,
                                                   @RequestParam(required = false) Integer dutyStatus) {
        SysUser user = userService.getById(id);
        int currentStatus = dutyStatus != null ? dutyStatus : (user != null && user.getDutyStatus() != null ? user.getDutyStatus() : 1);

        com.hc.drown.module.task.entity.LifeguardLocation loc = new com.hc.drown.module.task.entity.LifeguardLocation();
        loc.setUserId(id);
        loc.setAccuracy(new java.math.BigDecimal("10.0"));
        loc.setDutyStatus(currentStatus);

        if (dutyStatus != null) {
            jdbcTemplate.update("UPDATE sys_user SET duty_status = ? WHERE id = ?", dutyStatus, id);
        }

        return Result.success(rescueTaskService.uploadLocation(loc, lng, lat));
    }

    public static class NotifyParam {
        private Long userId;
        private String message;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
