package com.leyou.order.enums;

public enum OrderStatusEnum {
    UNPAY(1,"未付款"),
    PAYED(2,"已经付款"),
    DILIVERED(3,"已发货，未确认"),
    CONFRIMED(4,"已确认，未评价"),
    SUCCESS(5,"交易成功，未评价"),
    CLOSED(5,"交易关闭"),
    RATED(6,"已评价");




    private int code;
    private String describtion;
    OrderStatusEnum(int code, String describtion) {
        this.code = code;
        this.describtion = describtion;
    }
    public int  value(){
        return this.code;
    }
}
