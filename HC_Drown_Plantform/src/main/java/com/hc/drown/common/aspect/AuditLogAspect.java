package com.hc.drown.common.aspect;

import com.hc.drown.module.admin.entity.AuditLog;
import com.hc.drown.module.admin.service.AdminConfigService;
import com.hc.drown.module.user.dto.AdminUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AdminConfigService adminConfigService;

    @Pointcut("execution(* com.hc.drown.module.*.controller.*.*(..)) && !execution(* com.hc.drown.module.admin.controller.AdminConfigController.getAuditLogs(..))")
    public void controllerMethods() {}

    @Around("controllerMethods()")
    public Object profile(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        String resultStatus = "SUCCESS";
        Object result = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            resultStatus = "FAILED";
            throw t;
        } finally {
            try {
                long duration = System.currentTimeMillis() - start;
                HttpServletRequest request = null;
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    request = attributes.getRequest();
                }

                String username = "ANONYMOUS";
                Long userId = null;
                Object principal = SecurityContextHolder.getContext().getAuthentication() != null ?
                        SecurityContextHolder.getContext().getAuthentication().getPrincipal() : null;

                if (principal instanceof AdminUserDetails userDetails) {
                    username = userDetails.getUsername();
                    userId = userDetails.getSysUser().getId();
                }

                String methodName = joinPoint.getSignature().getName();
                String className = joinPoint.getTarget().getClass().getSimpleName();
                String moduleName = className.replace("Controller", "").toUpperCase();

                AuditLog auditLog = new AuditLog();
                auditLog.setUserId(userId);
                auditLog.setUsername(username);
                auditLog.setModule(moduleName);
                auditLog.setAction(methodName);
                auditLog.setResult(resultStatus);
                auditLog.setDetail(String.format("Executed method %s.%s. Time taken: %d ms", className, methodName, duration));
                if (request != null) {
                    auditLog.setIpAddress(request.getRemoteAddr());
                }

                adminConfigService.logAudit(auditLog);
            } catch (Exception e) {
                log.error("Failed to write audit log via AOP aspect", e);
            }
        }
    }
}
