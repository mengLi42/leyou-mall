package com.leyou.order.dto;

import com.leyou.common.dto.CartDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderDTO {  //DTO 数据传输对象
    @NotNull
    private  Long AddressId;
    @NotNull
    private  Integer paymentType;
    @NotNull
    private List<CartDTO> carts;
}
