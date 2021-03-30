package com.leyou.order.service;

import com.leyou.auth.entity.UserInfo;
import com.leyou.common.dto.CartDTO;
import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.common.utils.IdWorker;
import com.leyou.item.pojo.Sku;
import com.leyou.order.client.AddressClient;
import com.leyou.order.client.GoodsClient;
import com.leyou.order.dto.AddressDTO;
import com.leyou.order.dto.OrderDTO;
import com.leyou.order.enums.OrderStatusEnum;
import com.leyou.order.enums.PayState;
import com.leyou.order.interceptor.UserInterceptor;
import com.leyou.order.mapper.OrderDetailMapper;
import com.leyou.order.mapper.OrderMapper;
import com.leyou.order.mapper.OrderStatusMapper;
import com.leyou.order.pojo.Order;
import com.leyou.order.pojo.OrderDetail;
import com.leyou.order.pojo.OrderStatus;
import com.leyou.order.utils.PayHelper;
import io.swagger.models.auth.In;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;
@Slf4j
@Service

public class OrderService {
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper detailMapper;

    @Autowired
    private OrderStatusMapper statusMapper;
    @Autowired
    private IdWorker idWorker;
    @Autowired
    private GoodsClient goodsClient;
    @Autowired
    private PayHelper payHelper;

    @Transactional
    public Long CreateOrder(OrderDTO orderDTO){
        //1.组织订单的相关数据
        Order order=new Order();
         //1.1雪花算法生成订单ID ，全局唯一，64位的数据 包含时间戳-工作机器id-序列号等 重复概率几乎为0
        long orderId=idWorker.nextId();
        order.setOrderId(orderId);
        order.setCreateTime(new Date());
        order.setPaymentType(orderDTO.getPaymentType());
         //1.2获取用户信息
        UserInfo user= UserInterceptor.getLoginUser();
        order.setBuyerNick(user.getUsername());
        order.setBuyerRate(false);
         //1.3 收货人信息  项目中写了一个假的addressClient
        AddressDTO addr= AddressClient.findByID(orderDTO.getAddressId());
        order.setReceiver(addr.getName());
        order.setReceiverAddress(addr.getAddress());
        order.setReceiverState(addr.getState());
        order.setReceiverCity(addr.getCite());
        order.setReceiverDistrict(addr.getDistrict());
        order.setReceiverMobile(addr.getPhone());
        order.setReceiverZip(addr.getZipCode());
         //1.4 金额信息  orderDto中只有carts 因此要查出来所有商品 累加数量并计算
        Map<Long, Integer> numMap = orderDTO.getCarts().
                stream().collect(Collectors.toMap(CartDTO::getSkuId, CartDTO::getNum));
        Set<Long> ids = numMap.keySet();
        List<Sku> skuList = goodsClient.querySkuBySkuIds(new ArrayList<>(ids));
        List<OrderDetail> details=new ArrayList<>();
        long totalPay=0L;
        for (Sku sku : skuList) {
            //计算总价
            totalPay+=sku.getPrice()*numMap.get(sku.getId());
            //封装orderdetail
            OrderDetail detail=new OrderDetail();
            detail.setImage(StringUtils.substringBefore(sku.getImages(),","));
            detail.setNum(numMap.get(sku.getId()));
            detail.setOrderId(orderId);
            detail.setPrice(sku.getPrice());
            detail.setTitle(sku.getTitle());
            detail.setOwnSpec(sku.getOwnSpec());
            details.add(detail);

        }
        order.setTotalPay(totalPay);
        order.setActualPay(totalPay+order.getPostFee()-0);  //总金额+邮费-优惠
        //2.新增订单
        int count=orderMapper.insertSelective(order);
        if(count!=1){
            log.error("[创建订单] 创建订单失败，orderId:{}",orderId);
            throw new LyException(ExceptionEnum.CREATE_ORDER_ERROR);
        }
        //3.新增订单详情
        count=detailMapper.insertList(details);
        if(count!=1){
            log.error("[创建订单] 创建订单失败，orderId:{}",orderId);
            throw new LyException(ExceptionEnum.CREATE_ORDER_ERROR);
        }
        //4.新增订单状态
        OrderStatus orderStatus=new OrderStatus();
        orderStatus.setCreateTime(new Date());
        orderStatus.setOrderId(orderId);
        orderStatus.setStatus(OrderStatusEnum.UNPAY.value());
        count=statusMapper.insertSelective(orderStatus);
        if(count!=1){
            log.error("[创建订单] 创建订单失败，orderId:{}",orderId);
            throw new LyException(ExceptionEnum.CREATE_ORDER_ERROR);
        }
        //5.减库存  用feign同步调用比用rabbitMQ异步调用好
        List<CartDTO> cartDTOS=orderDTO.getCarts();
        goodsClient.decreaseStock(cartDTOS);
        return orderId;
    }

    /**
     * 根据id查询订单
     * @param id
     * @return
     */
    public Order queryOrderById(Long id) {
        Order order = orderMapper.selectByPrimaryKey(id);
        if(order==null)
            throw new LyException(ExceptionEnum.ORDER_NOT_FOUND);
        //查询订单详情
        OrderDetail detail=new OrderDetail();
        detail.setOrderId(id);
        List<OrderDetail> details=detailMapper.select(detail);
        if(CollectionUtils.isEmpty(details))
            throw new LyException(ExceptionEnum.ORDER_NOT_FOUND);
        order.setOrderDetails(details);
        //查询订单状态
        OrderStatus status=statusMapper.selectByPrimaryKey(id);
        if(status==null)
            throw new LyException(ExceptionEnum.ORDER_STATUS_EXCEPTION);
        order.setStatus(status);
        return order;
    }

    public void updateStatus(Long orderId, int i) {
        //修改状态 各种时间信息等
    }

    public String createPayUrl(Long orderId) {
        //查询订单
        Order order=queryOrderById(orderId);
        //判断状态
        Integer status =order.getStatus().getStatus();
        if(status!=OrderStatusEnum.UNPAY.value()){
            throw new LyException(ExceptionEnum.ORDER_STATUS_EXCEPTION);
        }
        //支付金额
        Long actualPay=order.getActualPay();
        String desc=order.getOrderDetails().get(0).getTitle();

        String url=payHelper.createPayUrl(orderId,actualPay,desc);
        return url;
    }

    public void handlerNotify(Map<String, String> result) {
        //1数据校验
            //判断通信、业务标识
       // payHelper.isSuccess(result);

        //2校验签名
        payHelper.isValidSign(result);
        //3校验金额。。
        String totalFeeStr=result.get("total_fee");
        String tradeNo=result.get("out_trade_no");
        if(StringUtils.isEmpty(totalFeeStr)||StringUtils.isEmpty(tradeNo))
            throw new LyException(ExceptionEnum.ORDER_NOT_FOUND);
        Long totalFee=Long.valueOf(totalFeeStr);
        Long orderId=Long.valueOf(tradeNo);
        if(totalFee!=orderMapper.selectByPrimaryKey(orderId).getActualPay())
            //金额不符
            throw  new LyException(ExceptionEnum.ORDER_NOT_FOUND);
        //4修改订单状态
        OrderStatus status=new OrderStatus();
        status.setStatus(OrderStatusEnum.PAYED.value());
        status.setOrderId(orderId);
        status.setPaymentTime(new Date());
        int count=statusMapper.updateByPrimaryKeySelective(status);
        if(count!=1)
            throw new LyException(ExceptionEnum.ORDER_STATUS_EXCEPTION);
    }

    public PayState queryOrderStateById(Long orderId) {
        OrderStatus orderStatus = statusMapper.selectByPrimaryKey(orderId);
        Integer status = orderStatus.getStatus();
        //判断是否支付
            //如果已经支付，则返回success
        if(status!=OrderStatusEnum.UNPAY.value())
            return PayState.SUCCESS;
            //如果未支付，但是结果不确定（wx可能通知失败） ，需要向wxpay查询状态
        return payHelper.queryPayState(orderId);
    }
}
