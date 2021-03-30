package com.leyou.item.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.leyou.common.dto.CartDTO;
import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.common.vo.PageResult;
import com.leyou.item.mapper.SkuMapper;
import com.leyou.item.mapper.SpuDetailMapper;
import com.leyou.item.mapper.SpuMapper;
import com.leyou.item.mapper.StockMapper;
import com.leyou.item.pojo.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import tk.mybatis.mapper.entity.Example;

import java.util.*;
import java.util.stream.Collectors;
@Service
public class GoodsService {
    @Autowired
    private SpuMapper spuMapper;

    @Autowired
    private SpuDetailMapper spuDetailMapper;

    @Autowired
    private  CategoryService categoryService;

    @Autowired
    private  BrandService brandService;
    @Autowired
    private SkuMapper skuMapper;
    @Autowired
    private StockMapper stockMapper;

    @Autowired
    private AmqpTemplate amqpTemplate;

    public PageResult<Spu> querySpuByPage(Integer page, Integer rows, Boolean saleable, String key) {
        //分页
        PageHelper.startPage(page,rows);

        //过滤
        Example example=new Example(Spu.class);
        Example.Criteria criteria= example.createCriteria();
        if(StringUtils.isNoneBlank(key)){
            criteria.andLike("title","%"+key+"%");
        }
        if(saleable!=null){
            criteria.andEqualTo("saleable",saleable);
        }
        //排序
        example.setOrderByClause("last_update_time DESC");
        //查询
        List<Spu> list= spuMapper.selectByExample(example);
        if(CollectionUtils.isEmpty(list)){
            throw new LyException(ExceptionEnum.GOODS_NOT_FOUND);
        }
        //解析分类名称和品牌名称
        loadCategoryAndBrandName(list);
        //解析分页的结果
        PageInfo<Spu> info=new PageInfo<>();
        return new PageResult<>(info.getTotal(),list);
    }

    private void loadCategoryAndBrandName(List<Spu> spus) {
        for (Spu spu : spus) {
            //categoryName
             List<String> names=categoryService.queryByIds(Arrays.asList(spu.getCid1(),spu.getCid2(),spu.getCid3()))
                     .stream().map(Category::getName).collect(Collectors.toList());
             spu.setCname(StringUtils.join(names,"/"));
            //brandName
             spu.setBname(brandService.queryById(spu.getBrandId()).getName());
        }
    }

    public void saveGoods(Spu spu) {
        //新增spu
        spu.setId(null);
        spu.setCreateTime(new Date());
        spu.setLastUpdateTime(spu.getCreateTime());
        spu.setSaleable(true);
        spu.setValid(false);

        int count=spuMapper.insert(spu);
        if(count!=1){
            throw new LyException(ExceptionEnum.GOODS_SAVE_ERROR);
        }
        //新增detail
        spu.getSpuDetail().setSpuId(spu.getId());
        this.spuDetailMapper.insert(spu.getSpuDetail());
        //新增sku
        //新增stock
        saveSkuAndStock(spu.getSkus(), spu.getId());

        //发送给rabbitMQ
        //this.amqpTemplate.convertAndSend("item.update",spu.getId());
    }
    private void saveSkuAndStock(List<Sku> skus, Long spuId) {
        //List<Stock> stockList=new ArrayList<>();
        for (Sku sku : skus) {
            if (!sku.getEnable()) {
                continue;
            }
            // 保存sku
            sku.setSpuId(spuId);
            // 默认不参与任何促销
            sku.setCreateTime(new Date());
            sku.setLastUpdateTime(sku.getCreateTime());
            this.skuMapper.insert(sku);

            // 保存库存信息
            Stock stock = new Stock();
            stock.setSkuId(sku.getId());
            stock.setStock(sku.getStock());
            this.stockMapper.insert(stock);
        }
        //批量新增
        //stockMapper.insertList(stockList);
    }

    public SpuDetail queryDetailById(Long spuId) {
        SpuDetail spuDetail=spuDetailMapper.selectByPrimaryKey(spuId);
        if(spuDetail==null)
             throw new LyException(ExceptionEnum.GOODS_NOT_FOUND);
        return spuDetail;
    }

    public List<Sku> querySkuBySpuId(Long spuId) {

        Sku sku=new Sku();
        sku.setSpuId(spuId);
        List<Sku> skuList=skuMapper.select(sku);
        if(CollectionUtils.isEmpty(skuList))
            throw new LyException((ExceptionEnum.SKU_NOT_FOUND));

        //需要将库存也查询并写入
/*        for(Sku s:skuList){
            Stock stock = stockMapper.selectByPrimaryKey(s.getId());
            if(stock==null)
                throw new LyException((ExceptionEnum.STOCK_NOT_FOUND));
            s.setStock(stock.getStock());
        }*/
        //批量查库存 然后写入
        List<Long> ids=skuList.stream().map(Sku::getId).collect(Collectors.toList());
        loadStockInSku(ids, skuList);
        return skuList;
    }

    public void updateGoods(Spu spu) {
        List<Sku> skus = this.querySkuBySpuId(spu.getId());
        // 如果以前存在，则删除
        if(!CollectionUtils.isEmpty(skus)) {
            List<Long> ids = skus.stream().map(s -> s.getId()).collect(Collectors.toList());
            // 删除以前库存
            Example example = new Example(Stock.class);
            example.createCriteria().andIn("skuId", ids);
            this.stockMapper.deleteByExample(example);

            // 删除以前的sku
            Sku record = new Sku();
            record.setSpuId(spu.getId());
            this.skuMapper.delete(record);

        }
        // 新增sku和库存
        saveSkuAndStock(spu.getSkus(), spu.getId());

        // 更新spu
        spu.setLastUpdateTime(new Date());
        spu.setCreateTime(null);
        spu.setValid(null);
        spu.setSaleable(null);
        this.spuMapper.updateByPrimaryKeySelective(spu);

        // 更新spu详情
        this.spuDetailMapper.updateByPrimaryKeySelective(spu.getSpuDetail());
    }

    public Spu querySpuById(Long id) {
        Spu spu=this.spuMapper.selectByPrimaryKey(id);
        if(spu==null)
            throw new LyException(ExceptionEnum.SPU_NOT_FOUND);
        //查sku
        spu.setSkus(querySkuBySpuId(id));
        //查detail
        spu.setSpuDetail(queryDetailById(id));
        return spu;
    }


    public List<Sku> querySkuBySkuIds(List<Long> ids) {
        List<Sku> skuList = skuMapper.selectByIdList(ids);
        if(CollectionUtils.isEmpty(skuList))
            throw new LyException(ExceptionEnum.SKU_NOT_FOUND);
        loadStockInSku(ids, skuList);
        return skuList;
    }

    private void loadStockInSku(List<Long> ids, List<Sku> skuList) {
        List<Stock> stockList = stockMapper.selectByIdList(ids);
        if (CollectionUtils.isEmpty(stockList))
            throw new LyException((ExceptionEnum.STOCK_NOT_FOUND));
        //把stock变成一个map 其key是skuid value是库存值
        Map<Long, Integer> stockMap = stockList.stream().collect(Collectors.toMap(Stock::getSkuId, Stock::getStock));
        skuList.forEach(sku1 -> sku1.setStock(stockMap.get(sku1.getId())));
    }
    @Transactional
    public void decreaseStock(List<CartDTO> carts) {
        for (CartDTO cart : carts) {
            //操作库存存在并发安全问题，
            //    集群模式可以用分布式锁（利用外部工具如zookeeper来模拟锁）来保证安全，但相当于单线程，性能很差
            //    采用乐观锁  不做查询和判断 ，在SQL语句中加入条件，在SQL内部判断
            int count=stockMapper.decreaseStock(cart.getSkuId(),cart.getNum());
            if(count!=1)
                throw new LyException(ExceptionEnum.STOCK_NOT_ENOUGH);

        }
    }
}
