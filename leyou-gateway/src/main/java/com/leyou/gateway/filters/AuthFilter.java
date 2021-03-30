package com.leyou.gateway.filters;

import com.leyou.auth.entity.UserInfo;
import com.leyou.auth.utils.JwtUtils;
import com.leyou.common.utils.CookieUtils;
import com.leyou.gateway.config.FilterProperties;
import com.leyou.gateway.config.JwtProperties;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.netflix.zuul.filters.support.FilterConstants;
import org.springframework.stereotype.Component;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

@Component
@EnableConfigurationProperties({JwtProperties.class, FilterProperties.class})
public class AuthFilter extends ZuulFilter {

    @Autowired
    private JwtProperties prop;
    @Autowired
    private FilterProperties filterProp;
    @Override
    public String filterType() {
        return FilterConstants.PRE_TYPE; //过滤器类型  前置过滤器
    }

    @Override
    public int filterOrder() {
        return FilterConstants.PRE_DECORATION_FILTER_ORDER-1;  //过滤器顺序  设置在官方过滤器之前
    }

    @Override
    public boolean shouldFilter() {
        //获取上下文
        RequestContext context=RequestContext.getCurrentContext();
        //获取request
        HttpServletRequest request=context.getRequest();
        //获取请求的url路径
        String requestURI = request.getRequestURI();  //得到的是域名：端口 之后的路径部分
        //判断是否放行
        for (String path : this.filterProp.getAllowPaths()) {
            // 然后判断是否是符合
            if(requestURI.startsWith(path)){
                return false; //在白名单中  不过滤
            }
        }

        return true;  //是否过滤
    }

    @Override  //自定义过滤器的逻辑
    public Object run() throws ZuulException {
        //获取上下文
        RequestContext context=RequestContext.getCurrentContext();
        //获取request
        HttpServletRequest request=context.getRequest();

        //获取token
        String token=CookieUtils.getCookieValue(request,prop.getCookieName());
        //解析token
        try {
            UserInfo user=JwtUtils.getInfoFromToken(token,prop.getPublicKey());
            //校验权限 TODO

        } catch (Exception e) {
            //解析失败
            context.setSendZuulResponse(false); //false即为拦截
            context.setResponseStatusCode(403);
        }

        return null;
    }
}
