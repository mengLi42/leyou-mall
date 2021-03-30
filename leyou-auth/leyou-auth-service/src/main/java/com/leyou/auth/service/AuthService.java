package com.leyou.auth.service;

import com.leyou.auth.client.UserClient;
import com.leyou.auth.config.JwtProperties;
import com.leyou.auth.entity.UserInfo;
import com.leyou.auth.utils.JwtUtils;
import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.user.pojo.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(JwtProperties.class)
@Slf4j
public class AuthService {
    @Autowired
    private UserClient userClient;
    @Autowired
    private JwtProperties prop;
    public String login(String username, String password) {
        //校验用户名和密码
        User user = userClient.queryUserByUserNameAndPsw(username, password);
        if(user==null)
            throw new LyException(ExceptionEnum.USERNAME_OR_PASSWORD_ERROR);
        //生成token 返回
        String token=null;
        try {
             token = JwtUtils.generateToken(new UserInfo(user.getId(), username), prop.getPrivateKey(), prop.getExpire());
        } catch (Exception e) {
            log.error("[授权中心]生成token失败，用户名称:{}",username,e);
            throw new LyException(ExceptionEnum.TOKEN_CREATE_EXCEPTION);
        }
        return token;
    }
}
