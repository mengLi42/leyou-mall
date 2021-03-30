package com.leyou.cart.service;

import com.leyou.auth.entity.UserInfo;
import com.leyou.cart.interceptor.UserInterceptor;
import com.leyou.cart.pojo.Cart;
import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.common.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartService {
    @Autowired
    private StringRedisTemplate redisTemplate;


    static final String KEY_PREFIX = "leyou:cart:uid:"; //业务前缀

    static final Logger logger = LoggerFactory.getLogger(CartService.class);

    public void addCart(Cart cart) {
        // 获取登录用户
        UserInfo user = UserInterceptor.getLoginUser();
        // Redis的key
        String key = KEY_PREFIX + user.getId();
        // 获取hash操作对象
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);
        // 查询是否存在
        Long skuId = cart.getSkuId();
        Integer num = cart.getNum();
        Boolean boo = hashOps.hasKey(skuId.toString());
        if (boo) {
            // 存在，获取购物车数据
            String json = hashOps.get(skuId.toString()).toString();
            cart = JsonUtils.toBean(json, Cart.class);
            // 修改购物车数量
            cart.setNum(cart.getNum() + num);
        }
        // 将购物车数据写入redis
        hashOps.put(cart.getSkuId().toString(), JsonUtils.toString(cart));
    }

    public List<Cart> queryCartList() {
        UserInfo user = UserInterceptor.getLoginUser();
        // Redis的key
        String key = KEY_PREFIX + user.getId();
        if(!redisTemplate.hasKey(key))
            throw new LyException(ExceptionEnum.CART_NOT_FOUDN);
        //获取用户的所有购物车数据
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);
        List<Cart> carts = hashOps.values().
                stream().map(o->JsonUtils.toBean(o.toString(),Cart.class)).collect(Collectors.toList());
        return carts;
    }
    /*
    修改购物车数量
     */
    public void updateCarts(Long skuId,Integer num) {
        // 获取登陆信息
        UserInfo userInfo = UserInterceptor.getLoginUser();
        String key = KEY_PREFIX + userInfo.getId();
        // 获取hash操作对象
        BoundHashOperations<String, Object, Object> hashOperations = this.redisTemplate.boundHashOps(key);
        // 获取购物车信息
        if(!hashOperations.hasKey(skuId.toString()))
            throw new LyException(ExceptionEnum.CART_NOT_FOUDN);
        String cartJson = hashOperations.get(skuId.toString()).toString();
        Cart cart = JsonUtils.toBean(cartJson, Cart.class);
        // 更新数量
        cart.setNum(num);
        // 写入购物车
        hashOperations.put(hashOperations.get(skuId.toString()), JsonUtils.toString(cart));
    }
    /**
     * 删除购物车
     */
    public void deleteCart(String skuId) {
        UserInfo user=UserInterceptor.getLoginUser();
        String key=KEY_PREFIX+user.getId();
        BoundHashOperations<String, Object, Object> hashOperations = redisTemplate.boundHashOps(key);
        hashOperations.delete(skuId);
    }
}