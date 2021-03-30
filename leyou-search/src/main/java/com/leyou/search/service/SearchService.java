package com.leyou.search.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.common.utils.JsonUtils;
import com.leyou.common.utils.NumberUtils;
import com.leyou.common.vo.PageResult;
import com.leyou.item.pojo.*;
import com.leyou.respository.GoodsRepository;
import com.leyou.search.client.BrandClient;
import com.leyou.search.client.CategoryClient;
import com.leyou.search.client.GoodsClient;
import com.leyou.search.client.SpecificationClient;
import com.leyou.search.pojo.Goods;
import com.leyou.search.pojo.SearchRequest;
import com.leyou.search.pojo.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.LongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;
@Slf4j
@Service
public class SearchService {
    @Autowired
    private BrandClient brandClient;
    @Autowired
    private CategoryClient categoryClient;
    @Autowired
    private GoodsClient goodsClient;
    @Autowired
    private SpecificationClient specificationClient;
    @Autowired
    private GoodsRepository repository;
    @Autowired
    private ElasticsearchTemplate template;
    public  PageResult<Goods> search(SearchRequest request) {
        Integer page = request.getPage()-1;//elasticSearch中page默认从0开始
        Integer size = request.getSize();
        //创建查询构建器
        NativeSearchQueryBuilder queryBuilder=new NativeSearchQueryBuilder();
        //搜索过略  过滤掉 过滤字段
        queryBuilder.withSourceFilter(new FetchSourceFilter(new String[]{"id","subTitle","skus"},null));
        //分页
        queryBuilder.withPageable(PageRequest.of(page,size));
        //查询条件
        //QueryBuilder basicQuery=QueryBuilders.matchQuery("all",request.getKey());
        //根据request构建基本查询
        QueryBuilder basicQuery=buildBasicQuery(request);
        queryBuilder.withQuery(basicQuery);
        //聚合分类和品牌
            //聚合分类
        String categoryAggName="category_agg";
        queryBuilder.addAggregation(AggregationBuilders.terms(categoryAggName).field("cid3"));
            //聚合品牌
        String brandAggName="brand_agg";
        queryBuilder.addAggregation(AggregationBuilders.terms(brandAggName).field("brandId"));
        //查询
        //Page<Goods> result = repository.search(queryBuilder.build());
        //拿聚合结果需要用template 不能用respository
        AggregatedPage<Goods> result = template.queryForPage(queryBuilder.build(), Goods.class);
        //解析结果
            //分页结果
        long total = result.getTotalElements();
        int totalPages = result.getTotalPages();
        List<Goods> goodsList=result.getContent();
            //聚合结果
        Aggregations aggs = result.getAggregations();
        List<Category> categories=parseCategoryAgg(aggs.get(categoryAggName));
        List<Brand> brands=parseBrandAgg(aggs.get(brandAggName));
            //规格参数聚合
        List<Map<String,Object>> specs=null;
        if(categories!=null&& categories.size()==1){
            specs=buildSpecificationAgg(categories.get(0).getId(),basicQuery);
        }
        //返回字段太多 null   需要在application中配置 过略掉
        return new SearchResult(total,totalPages,goodsList,categories,brands,specs);
    }

    private QueryBuilder buildBasicQuery(SearchRequest request) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        // 基本查询条件
        queryBuilder.must(QueryBuilders.matchQuery("all", request.getKey()).operator(Operator.AND));
        // 过滤条件构建器
        BoolQueryBuilder filterQueryBuilder = QueryBuilders.boolQuery();
        // 整理过滤条件
        Map<String, String> filter = request.getFilter();
        for (Map.Entry<String, String> entry : filter.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            // 商品分类和品牌要特殊处理
            if (key != "cid3" && key != "brandId") {
                key = "specs." + key + ".keyword";
            }
            // 字符串类型，进行term查询
            filterQueryBuilder.must(QueryBuilders.termQuery(key, value));
        }
        // 添加过滤条件
        queryBuilder.filter(filterQueryBuilder);
        return queryBuilder;
    }

    private List<Map<String, Object>> buildSpecificationAgg(Long cid, QueryBuilder basicQuery) {
        List<Map<String, Object>> specs=new ArrayList<>();
        //查询需要聚合的规格参数
        List<SpecParam> params = specificationClient.queryParamList(cid, null, true);
        //聚合
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
            //带上查询条件
        queryBuilder.withQuery(basicQuery);
            //
        queryBuilder.withPageable(PageRequest.of(0, 1));
        for (SpecParam param : params) {
            String name=param.getName();
            queryBuilder.addAggregation(AggregationBuilders.terms(name).field("specs."+name+".keyword"));
        }
        //获取结果
        AggregatedPage<Goods> result = template.queryForPage(queryBuilder.build(), Goods.class);
        //解析结果
        Aggregations aggs = result.getAggregations();
        for (SpecParam param:params) {
            String name=param.getName();
            Terms terms=aggs.get(name);
            //准备map
            Map<String ,Object> map=new HashMap<>();
            map.put("k",name);
            map.put("options",terms.getBuckets().
                    stream().map(bucket -> bucket.getKey()).collect(Collectors.toList()));
            specs.add(map);
        }
        return specs;
    }

    private List<Brand> parseBrandAgg(LongTerms terms) { //聚合得到的是id  用LongTerms
        try {
            List<Long> ids = terms.getBuckets()
                    .stream().map(bucket -> bucket.getKeyAsNumber().longValue()).collect(Collectors.toList());

            List<Brand> brands = brandClient.queryBrandByIds(ids);
            return brands;
        }catch (Exception e){
            log.error("查询品牌异常");
            return null;
        }
    }

    private List<Category> parseCategoryAgg(LongTerms terms) {
        try {

            List<Long> ids = terms.getBuckets()
                    .stream().map(bucket -> bucket.getKeyAsNumber().longValue()).collect(Collectors.toList());
            List<Category> categories = categoryClient.queryCategoryByIds(ids);
            return categories;
        }catch (Exception e){
            log.error("查询分类异常");
            return null;
        }
    }

    /**
     * 将一个spu构建成一个goods
     * @param spu
     * @return
     */

    public Goods buildGoods(Spu spu){
        //查询分类
        List<Category> categories = categoryClient.queryCategoryByIds(
                Arrays.asList(spu.getCid1(), spu.getCid2(), spu.getCid3()));
        if(CollectionUtils.isEmpty(categories))
            new LyException(ExceptionEnum.CATEGORY_NOT_FOUND);
        List<String> categoryNames=categories.stream().map(Category::getName).collect(Collectors.toList());

        //查询品牌
        Brand brand=brandClient.queryBrandById(spu.getBrandId());
        String brandName=brand.getName();
        //查询字段
        List<Sku> skuList = goodsClient.querySkuBySpuId(spu.getId());
        if(CollectionUtils.isEmpty(skuList))
            new LyException(ExceptionEnum.SKU_NOT_FOUND);

        //对sku进行处理  滤掉不需要的信息
        List<Map<String,Object>> skus=new ArrayList<>();
        Set<Long> priceList=new HashSet<>();
        for (Sku sku : skuList) {
            Map<String,Object> map=new HashMap<>();
            map.put("id",sku.getId());
            map.put("title",sku.getTitle());
            map.put("price",sku.getPrice());
            map.put("image",StringUtils.substringBefore(sku.getImages(),","));
            skus.add(map);
            //处理价格
            priceList.add(sku.getPrice());
        }
        //Set<Long> priceList = skuList.stream().map(Sku::getPrice).collect(Collectors.toSet());
        //规格参数
        Map<String,Object> specs=new HashMap<>();
        List<SpecParam> params = specificationClient.queryParamList(spu.getCid3(), null, true);
        if(CollectionUtils.isEmpty(params))
            throw new LyException(ExceptionEnum.SPEC_PARAM_NOT_FOUND);
        SpuDetail spuDetail=goodsClient.querySpuDetailById(spu.getId());
        //通用规格参数
        Map<String, String> genericSpec = JsonUtils.toMap(spuDetail.getGenericSpec(), String.class, String.class);
        //特有规格参数
        Map<String, List<String>> specialSpec = JsonUtils
                .nativeRead(spuDetail.getSpecialSpec(), new TypeReference<Map<String, List<String>>>() {
        });
        // spec的 key为规格参数名，key为规格参数值
        for (SpecParam param : params) {
            String key=param.getName();

            if(param.getGeneric()){
                String value=genericSpec.get(param.getId().toString());
                //判断是否是数值类型
                if(param.getNumeric()){
                    //数值类型的  处理成 “段”类型的
                    value=chooseSegment(value,param);
                }
            specs.put(key,value) ;
            }else{
                //value=specialSpec.get(param.getId());
            specs.put(key,specialSpec.get(param.getId().toString()));
            }
        }
        String all=spu.getTitle()+ StringUtils.join(categoryNames," ")+brandName;

        Goods goods=new Goods();
        goods.setBrandId(spu.getBrandId());
        goods.setCid1(spu.getCid1());
        goods.setCid2(spu.getCid2());
        goods.setCid3(spu.getCid3());
        goods.setCreateTime(spu.getCreateTime());
        goods.setId(spu.getId());
        goods.setSubTitle(spu.getSubTitle());




        goods.setAll(all); //搜索字段 包含各种搜索需要的信息，标题 分类 品牌 规格等等
        goods.setPrice(priceList);//所有sku价格的集合
        goods.setSkus(JsonUtils.toString(skus));//所有sku集合的json格式
        goods.setSpecs(specs);//可搜索的规格参数;

        return goods;
    }

    //处理段
    private String chooseSegment(String value, SpecParam p) {
        double val = NumberUtils.toDouble(value);
        String result = "其它";
        // 保存数值段
        for (String segment : p.getSegments().split(",")) {
            String[] segs = segment.split("-");
            // 获取数值范围
            double begin = NumberUtils.toDouble(segs[0]);
            double end = Double.MAX_VALUE;
            if(segs.length == 2){
                end = NumberUtils.toDouble(segs[1]);
            }
            // 判断是否在范围内
            if(val >= begin && val < end){
                if(segs.length == 1){
                    result = segs[0] + p.getUnit() + "以上";
                }else if(begin == 0){
                    result = segs[1] + p.getUnit() + "以下";
                }else{
                    result = segment + p.getUnit();
                }
                break;
            }
        }
        return result;
    }
}
