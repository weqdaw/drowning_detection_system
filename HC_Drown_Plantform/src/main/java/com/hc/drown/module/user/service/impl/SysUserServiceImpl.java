package com.hc.drown.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hc.drown.common.exception.BusinessException;
import com.hc.drown.common.util.JwtTokenUtil;
import com.hc.drown.module.user.dto.AdminUserDetails;
import com.hc.drown.module.user.entity.SysPermission;
import com.hc.drown.module.user.entity.SysRole;
import com.hc.drown.module.user.entity.SysUser;
import com.hc.drown.module.user.mapper.SysUserMapper;
import com.hc.drown.module.user.mapper.SysRoleMapper;
import com.hc.drown.module.user.mapper.SysUserRoleMapper;
import com.hc.drown.module.user.entity.SysUserRole;
import com.hc.drown.module.user.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser>
        implements SysUserService, UserDetailsService {

    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final com.hc.drown.module.task.mapper.LifeguardLocationMapper lifeguardLocationMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = getByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        List<SysPermission> permissionList = getPermissions(user.getId());
        return new AdminUserDetails(user, permissionList);
    }

    @Override
    public SysUser getByUsername(String username) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getDeleted, 0));
    }

    @Override
    public String login(String username, String password) {
        String token = null;
        try {
            UserDetails userDetails = loadUserByUsername(username);
            if (!passwordEncoder.matches(password, userDetails.getPassword())) {
                throw new BusinessException("密码不正确");
            }
            if (!userDetails.isEnabled()) {
                throw new BusinessException("帐号已被禁用");
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            token = jwtTokenUtil.generateToken(userDetails);

            // 更新登录时间
            SysUser user = ((AdminUserDetails) userDetails).getSysUser();
            user.setLastLoginAt(LocalDateTime.now());
            updateById(user);

        } catch (UsernameNotFoundException e) {
            throw new BusinessException("用户名不存在");
        }
        return token;
    }

    @Override
    public String refreshToken(String oldToken) {
        return null; // 可供补充
    }

    @Override
    public List<SysRole> getRoles(Long userId) {
        return userMapper.selectRolesByUserId(userId);
    }

    @Override
    public List<SysPermission> getPermissions(Long userId) {
        return userMapper.selectPermissionsByUserId(userId);
    }

    @Override
    @Transactional
    public SysUser register(SysUser sysUser, String roleCode) {
        if (getByUsername(sysUser.getUsername()) != null) {
            throw new BusinessException("用户名已存在");
        }
        if (sysUser.getGuardId() != null && !sysUser.getGuardId().trim().isEmpty()) {
            Long count = baseMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getGuardId, sysUser.getGuardId().trim())
                    .eq(SysUser::getDeleted, 0));
            if (count != null && count > 0) {
                throw new BusinessException("救生员编号已存在");
            }
        }
        sysUser.setPassword(passwordEncoder.encode(sysUser.getPassword()));
        sysUser.setStatus(1);
        sysUser.setDutyStatus(0);
        sysUser.setCreatedAt(LocalDateTime.now());
        sysUser.setUpdatedAt(LocalDateTime.now());
        sysUser.setDeleted(0);
        sysUser.setPasswordReset(false);
        baseMapper.insert(sysUser);

        String targetRole = (roleCode != null && !roleCode.trim().isEmpty()) ? roleCode : "ROLE_RESCUER";
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, targetRole)
                .eq(SysRole::getDeleted, 0));
        if (role == null) {
            role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleCode, "ROLE_RESCUER")
                    .eq(SysRole::getDeleted, 0));
        }
        if (role != null) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(sysUser.getId());
            userRole.setRoleId(role.getId());
            userRoleMapper.insert(userRole);
        }

        return sysUser;
    }

    @Override
    public List<SysUser> findActiveLifeguards() {
        return lifeguardLocationMapper.findActiveLifeguards();
    }
}
