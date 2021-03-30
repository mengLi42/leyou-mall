package com.leyou.order.client;

import com.leyou.order.dto.AddressDTO;

import java.util.ArrayList;
import java.util.List;

public class AddressClient {
    public static final List<AddressDTO> addressList =new ArrayList<AddressDTO>(){
        {
            AddressDTO address1=new AddressDTO();
            address1.setId(1L);
            address1.setAddress("太白南路2号");
            address1.setState("陕西");
            address1.setCite("西安");
            address1.setIsDefault(true);
            address1.setPhone("12345678910");
            address1.setName("张三");
        }
    };
    public static  AddressDTO findByID(Long id){
        for(AddressDTO addressDTO:addressList){
            if(addressDTO.getId()==id)
                return addressDTO;
        }
        return  null;
    }
}
