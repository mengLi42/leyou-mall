package com.leyou.user.web;

import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.user.pojo.User;
import com.leyou.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.stream.Collectors;

@RestController
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("check/{data}/{type}")
    public ResponseEntity<Boolean> checkData(
            @PathVariable("data")String data,@PathVariable("type")Integer type){
        Boolean boo = this.userService.checkData(data, type);
        if (boo == null) {
            throw new LyException(ExceptionEnum.INVALID_USER_DATA);
        }
        return ResponseEntity.ok(boo);

    }

    @GetMapping("code")
    public ResponseEntity<Void> sendCode(@PathVariable("phont")String phone){
        userService.sendCode(phone);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

    }

    @PostMapping("register")
    public ResponseEntity<Void> register(@Valid User user, BindingResult result, @RequestParam("code") String code){
        if(result.hasFieldErrors()){ //参数中定义result  会返回自定义的结果  否则，返回springMVC设置的默认错误格式
            throw new RuntimeException(result.getFieldErrors()
                    .stream().map(e->e.getDefaultMessage()).collect(Collectors.joining("|")));
        }
        userService.register(user,code);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/query")
    public ResponseEntity<User> queryUserByUserNameAndPsw(
            @RequestParam("username") String username,
            @RequestParam("password") String password
    ){
        return ResponseEntity.ok(userService.queryUserByUsernameAndPsw(username,password));
    }
}
