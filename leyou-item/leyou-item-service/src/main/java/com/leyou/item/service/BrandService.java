package com.leyou.item.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.common.vo.PageResult;
import com.leyou.item.mapper.BrandMapper;
import com.leyou.item.pojo.Brand;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import tk.mybatis.mapper.entity.Example;

import java.util.List;

@Service
public class BrandService {
    @Autowired
    private BrandMapper brandMapper;

    public PageResult<Brand> queryBrandByPageAndSort(Integer page, Integer rows, String sortBy, Boolean desc, String key) {

            // 开始分页  在
            PageHelper.startPage(page, rows);
            // 过滤
            Example example = new Example(Brand.class);
            if (StringUtils.isNotBlank(key)) {
                example.createCriteria().andLike("name", "%" + key + "%")
                        .orEqualTo("letter", key);
            }
            if (StringUtils.isNotBlank(sortBy)) {
                // 排序
                String orderByClause = sortBy + (desc ? " DESC" : " ASC");
                example.setOrderByClause(orderByClause);
            }
            // 查询
            Page<Brand> pageInfo = (Page<Brand>) brandMapper.selectByExample(example);
            // 返回结果
            return new PageResult<>(pageInfo.getTotal(), pageInfo);
        }
    @Transactional
    public void saveBrand(Brand brand, List<Long> cids) {
        //新增品牌
        brand.setId(null);
        int count=brandMapper.insert(brand);  //新增完成 id会自动会写  因此下面for中getid 不会为null
        if(count!=1){
            throw new LyException(ExceptionEnum.BRAND_CREATE_FAILED);
        }
        //新增分类-品牌中间表   中间表没有实体类因此不能用通用mapper
        for(Long cid:cids){
           count= brandMapper.insertCategoryBrand(cid,brand.getId());
           if(count!=1)
               throw  new LyException((ExceptionEnum.BRAND_CREATE_FAILED));
        }
    }
    public Brand queryById(Long id){
        Brand brand=brandMapper.selectByPrimaryKey(id);
        if(brand==null)
            throw new LyException(ExceptionEnum.BRAND_NOT_FOUND);
        return brand;
    }
    //多表查询需要自定义sql

    public List<Brand> queryBrandByCategory(Long cid) {
        return this.brandMapper.queryByCategoryId(cid);
    }

    public List<Brand> queryByIds(List<Long> ids) {
        List<Brand> brands=this.brandMapper.selectByIdList(ids);
        if(CollectionUtils.isEmpty(brands))
            throw new LyException(ExceptionEnum.BRAND_NOT_FOUND);

        return brands;
    }
}

