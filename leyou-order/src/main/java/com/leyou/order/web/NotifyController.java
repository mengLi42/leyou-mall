package com.leyou.order.web;

import com.leyou.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("notify")
public class NotifyController {
    @Autowired
    private OrderService orderService;
    @PostMapping(value = "pay",produces = "applicaiton/xml")
    public Map<String, String> hello(@RequestBody Map<String,String> result){  //spring自动判断接收到的数据格式然后用转换器
        //此处用xml转换器
        orderService.handlerNotify(result);
        //处理回调
        Map<String,String> msg=new HashMap<>();
        msg.put("return_code","SUCCESS");
        msg.put("return_msg","OK");
        return msg;  //返回结果也自动封装成xml
    }
}
