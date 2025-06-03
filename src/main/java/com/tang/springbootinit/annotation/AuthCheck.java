package com.tang.springbootinit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验
 *
 *  @author
 *  @from
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 必须有某个角色
     *
     * @return
     */
    String mustRole() default "";

}


/**
 * 这是一个自定义注解，用于方法级别的权限校验，允许指定一个必须具备的角色（mustRole属性）
 *
 * @Target：这是一个元注解，用于指定自定义注解可以应用的程序元素。
 * ElementType.METHOD：表示该注解只能应用于方法上。
 *
 * @Retention：这是另一个元注解，用于指定注解的生命周期。
 * RetentionPolicy.RUNTIME：表示该注解在运行时可用（通过反射可以获取到）。
 */