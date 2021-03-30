package com.leyou.auth.web;

import com.leyou.auth.config.JwtProperties;
import com.leyou.auth.entity.UserInfo;
import com.leyou.auth.service.AuthService;
import com.leyou.auth.utils.JwtUtils;
import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.common.utils.CookieUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@EnableConfigurationProperties(JwtProperties.class)
public class AuthController {
    @Autowired
    private JwtProperties prop;

    @Autowired
    private AuthService authService;
    @Value("${leyou.jwt.cookieName}")
    private  String cookieName;

    @PostMapping("login") //前端无须解析token  因此不需要返回给前端，只需将token写入cookie，因此返回void
    public ResponseEntity<Void> login(@RequestParam("username")String username,
                                      @RequestParam("password")String password,
                                      HttpServletRequest request, HttpServletResponse response
                                        ){
        String token=authService.login(username,password);
        //将token写入用户的cookie

        CookieUtils.newBuilder(response).httpOnly().request(request).build(cookieName,token);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * 确定登录状态  即从cookie中解析token
     * @return
     */
    @GetMapping("verify")
    public ResponseEntity<UserInfo> verify(@CookieValue("LY_TOKEN")String token,
                                           HttpServletRequest request,
                                           HttpServletResponse response){

        try {
            UserInfo info=JwtUtils.getInfoFromToken(token,prop.getPublicKey());
            //刷新token  即重新生成并写入cookie
            String newToken=JwtUtils.generateToken(info,prop.getPrivateKey(),prop.getExpire());
            CookieUtils.newBuilder(response).httpOnly().request(request).build(cookieName,newToken);

            return ResponseEntity.ok(info);
        } catch (Exception e) {
            //token过期或者被篡改
            throw new LyException(ExceptionEnum.UNAUTHORIZED_EXCEPTION);
        }

    }
}
