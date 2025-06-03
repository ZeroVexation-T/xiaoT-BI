package com.tang.springbootinit.aop;

import com.tang.springbootinit.annotation.AuthCheck;
import com.tang.springbootinit.common.ErrorCode;
import com.tang.springbootinit.exception.BusinessException;
import com.tang.springbootinit.model.entity.User;
import com.tang.springbootinit.model.enums.UserRoleEnum;
import com.tang.springbootinit.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 权限校验 AOP
 *
 *  @author
 *  @from
 */
@Aspect
@Component
public class AuthInterceptor {

    private UserService userService;

    /**
     * 执行拦截
     *
     * @param joinPoint
     * @param authCheck
     * @return
     */
    // 定义环绕通知，拦截所有带有@AuthCheck注解的方法
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {

        String mustRole = authCheck.mustRole();

        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

        // 当前登录用户
        User loginUser = userService.getLoginUser(request);

        // 必须有该权限才通过
        if (StringUtils.isNotBlank(mustRole)) {
            UserRoleEnum mustUserRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
            if (mustUserRoleEnum == null) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            String userRole = loginUser.getUserRole();
            // 如果被封号，直接拒绝
            if (UserRoleEnum.BAN.equals(mustUserRoleEnum)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            // 必须有管理员权限
            if (UserRoleEnum.ADMIN.equals(mustUserRoleEnum)) {
                if (!mustRole.equals(userRole)) {
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
                }
            }
        }
        // 通过权限校验，放行
        return joinPoint.proceed();
    }
}

/**
 * @Aspect，声明一个类为切面类
 * 这样的类其中可以包含：
 * 1、切入点（Pointcut）：定义哪些方法需要被拦截
 * 2、通知（Advice）：定义拦截到方法后执行的逻辑
 * 3、引介（Introduction）：为现有类型添加新的方法或属性（较少使用）
 * 与 @Component 结合使用，才能被Spring管理
 */