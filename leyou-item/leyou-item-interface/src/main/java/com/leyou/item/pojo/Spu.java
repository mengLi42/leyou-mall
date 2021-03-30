package com.leyou.item.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import tk.mybatis.mapper.annotation.KeySql;

import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.util.Date;
import java.util.List;

/**
 * @author bystander
 * @date 2018/9/18
 */
@Table(name = "tb_spu")
@Data
public class Spu {

    @Id
    @KeySql(useGeneratedKeys = true)
    private Long id;
    private String title;
    private String subTitle;
    private Long cid1;
    private Long cid2;
    private Long cid3;
    private Long brandId;
    private Boolean saleable;
    private Boolean valid;
    private Date createTime;

    @JsonIgnore  // 返回界面的时候忽略此字段
    private Date lastUpdateTime;

    @Transient  //非数据库中表字段 需要注释非持久 否则通用mapper出错
    private String cname;
    @Transient
    private String bname;

    //下面两个字段 因为post请求提交的是json 表示一个spu对象， 不加的话json和spu对象不吻合
    //spu详情
    @Transient
    private SpuDetail spuDetail;

    //sku集合
    @Transient
    private List<Sku> skus;



}
