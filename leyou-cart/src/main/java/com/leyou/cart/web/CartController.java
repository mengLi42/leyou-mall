package com.leyou.cart.web;

import com.leyou.cart.pojo.Cart;
import com.leyou.cart.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CartController {

    @Autowired
    private CartService cartService;

    /**
     * 添加购物车内容
     * @return
     */
    @PostMapping  //请求路径是cart 在gateway中已经映射过了 ，因此 此处没有参数
    public ResponseEntity<Void> addCart(@RequestBody Cart cart) {
        this.cartService.addCart(cart);
        return ResponseEntity.ok().build();
    }
    //查询购物车
    @GetMapping
    public ResponseEntity<List<Cart>> queryCartList(){
        return  ResponseEntity.ok(cartService.queryCartList());
    }
    //修改购物车数量
    @PutMapping
    public ResponseEntity<Void> updateNum(@RequestParam("id")Long skuId,
                                          @RequestParam("num")Integer num){
        this.cartService.updateCarts(skuId,num);
        return ResponseEntity.noContent().build();
    }

    //删除购物车
    @DeleteMapping("{skuId}")
    public ResponseEntity<Void> deleteCart(@PathVariable("skuId") String skuId) {
        this.cartService.deleteCart(skuId);
        return ResponseEntity.ok().build();
    }
}