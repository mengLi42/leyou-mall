package com.leyou.order.config;


import com.leyou.order.interceptor.UserInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@EnableConfigurationProperties(JwtProperties.class)
//实现mvcconfiguer接口 重现添加拦截器的方法，即可配置自定义的拦截器
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private JwtProperties prop;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new UserInterceptor(prop)).addPathPatterns("/**");
            }
}
