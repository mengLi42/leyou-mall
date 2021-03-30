package com.leyou.user.service;

import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.common.utils.NumberUtils;
import com.leyou.user.mapper.UserMapper;
import com.leyou.user.pojo.User;
import com.leyou.user.utils.CodecUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AmqpTemplate amqpTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PRIFIX="user:verify:phone";

    public Boolean checkData(String data, Integer type) {
        User record = new User();
        switch (type) {
            case 1:
                record.setUsername(data);
                break;
            case 2:
                record.setPhone(data);
                break;
            default:
                return null;
        }
        return this.userMapper.selectCount(record) == 0; //只查count 比查user对象 性能更好
    }


    public void sendCode(String phone) {
        String key=KEY_PRIFIX+phone;
        String code= NumberUtils.generateCode(6);//随机生成六位的随机数作为验证码
        Map<String,String> msg=new HashMap<>();
        msg.put("phone",phone);
        msg.put("code",code);
        //发送验证码
        amqpTemplate.convertAndSend("ly.sms.exchange","sms.verify.code",msg);
        //保存验证码到redis
        redisTemplate.opsForValue().set(key,code,5, TimeUnit.MINUTES);

    }

    public void register(User user, String code) {
        //校验验证码
        String cacheCode=redisTemplate.opsForValue().get(KEY_PRIFIX+user.getPhone());
      /*  if(!StringUtils.equals(code,cacheCode)){
            throw new LyException(ExceptionEnum.VERIFY_CODE_NOT_MATCHING);
        }*/
        //对密码进行加密  MD5
        String salt= CodecUtils.generateSalt();
        user.setSalt(salt);
        user.setPassword(CodecUtils.md5Hex(user.getPassword(),salt));
        //将数据写入数据库
        user.setCreated(new Date());
        userMapper.insert(user);
    }

    public User queryUserByUsernameAndPsw(String username, String password) {
        User record=new User();
        record.setUsername(username);
        //record.setPassword(CodecUtils...); 此处不能查密码，因为没有用户的salt  此外
        //  数据库中对username做了索引，同时对名字和密码查询则会降低效率
        User user=userMapper.selectOne(record);  //因此只查用户名
        if(user==null){
            throw new LyException(ExceptionEnum.USERNAME_OR_PASSWORD_ERROR);
        }
        //校验密码   根据查到的盐和密文 对比
       /* if(!StringUtils.equals(user.getPassword(),CodecUtils.md5Hex(password,user.getSalt())))
            throw new LyException(ExceptionEnum.USERNAME_OR_PASSWORD_ERROR); */
        return user;
    }
}
