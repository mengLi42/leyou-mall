package com.leyou.cart.interceptor;

import com.leyou.auth.entity.UserInfo;
import com.leyou.auth.utils.JwtUtils;
import com.leyou.cart.config.JwtProperties;
import com.leyou.common.utils.CookieUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class UserInterceptor extends HandlerInterceptorAdapter {

    private JwtProperties jwtProperties;

    // 定义一个线程域，存放登录用户
    //threadLocal 是以是一个map结构 key为线程本身（因此存的时候用set值即可，不需要指定key），value为存进去的对象
    private static final ThreadLocal<UserInfo> tl = new ThreadLocal<>();
    //此处通过构造方法注释，而不是autowired注入 因为在mvc配置中使用的是 自己new 拦截器，而不是spring自己生成拦截器
    public UserInterceptor(JwtProperties prop){
        this.jwtProperties=prop;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 查询token
        String token = CookieUtils.getCookieValue(request, "LY_TOKEN");
        if (StringUtils.isBlank(token)) {
            // 未登录,返回401
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
        // 有token，查询用户信息
        try {
            // 解析成功，证明已经登录
            UserInfo user = JwtUtils.getInfoFromToken(token, jwtProperties.getPublicKey());
            // 放入线程域
            tl.set(user);
            //也可以放入request域 但是SpringMvc不推荐使用 request域 ，因此使用线程域  一次请求过程共享一个线程
            // request.setAttribute("user",user);
            return true;
        } catch (Exception e){
            // 抛出异常，证明未登录,返回401
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // afterComletion 在视图渲染结束后执行，此时清空线程域中的user
        tl.remove();
    }

    public static UserInfo getLoginUser() {
        return tl.get();
    }

}
