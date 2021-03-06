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
        Integer page = request.getPage()-1;//elasticSearch???page?????????0??????
        Integer size = request.getSize();
        //?????????????????????
        NativeSearchQueryBuilder queryBuilder=new NativeSearchQueryBuilder();
        //????????????  ????????? ????????????
        queryBuilder.withSourceFilter(new FetchSourceFilter(new String[]{"id","subTitle","skus"},null));
        //??????
        queryBuilder.withPageable(PageRequest.of(page,size));
        //????????????
        //QueryBuilder basicQuery=QueryBuilders.matchQuery("all",request.getKey());
        //??????request??????????????????
        QueryBuilder basicQuery=buildBasicQuery(request);
        queryBuilder.withQuery(basicQuery);
        //?????????????????????
            //????????????
        String categoryAggName="category_agg";
        queryBuilder.addAggregation(AggregationBuilders.terms(categoryAggName).field("cid3"));
            //????????????
        String brandAggName="brand_agg";
        queryBuilder.addAggregation(AggregationBuilders.terms(brandAggName).field("brandId"));
        //??????
        //Page<Goods> result = repository.search(queryBuilder.build());
        //????????????????????????template ?????????respository
        AggregatedPage<Goods> result = template.queryForPage(queryBuilder.build(), Goods.class);
        //????????????
            //????????????
        long total = result.getTotalElements();
        int totalPages = result.getTotalPages();
        List<Goods> goodsList=result.getContent();
            //????????????
        Aggregations aggs = result.getAggregations();
        List<Category> categories=parseCategoryAgg(aggs.get(categoryAggName));
        List<Brand> brands=parseBrandAgg(aggs.get(brandAggName));
            //??????????????????
        List<Map<String,Object>> specs=null;
        if(categories!=null&& categories.size()==1){
            specs=buildSpecificationAgg(categories.get(0).getId(),basicQuery);
        }
        //?????????????????? null   ?????????application????????? ?????????
        return new SearchResult(total,totalPages,goodsList,categories,brands,specs);
    }

    private QueryBuilder buildBasicQuery(SearchRequest request) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        // ??????????????????
        queryBuilder.must(QueryBuilders.matchQuery("all", request.getKey()).operator(Operator.AND));
        // ?????????????????????
        BoolQueryBuilder filterQueryBuilder = QueryBuilders.boolQuery();
        // ??????????????????
        Map<String, String> filter = request.getFilter();
        for (Map.Entry<String, String> entry : filter.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            // ????????????????????????????????????
            if (key != "cid3" && key != "brandId") {
                key = "specs." + key + ".keyword";
            }
            // ????????????????????????term??????
            filterQueryBuilder.must(QueryBuilders.termQuery(key, value));
        }
        // ??????????????????
        queryBuilder.filter(filterQueryBuilder);
        return queryBuilder;
    }

    private List<Map<String, Object>> buildSpecificationAgg(Long cid, QueryBuilder basicQuery) {
        List<Map<String, Object>> specs=new ArrayList<>();
        //?????????????????????????????????
        List<SpecParam> params = specificationClient.queryParamList(cid, null, true);
        //??????
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
            //??????????????????
        queryBuilder.withQuery(basicQuery);
            //
        queryBuilder.withPageable(PageRequest.of(0, 1));
        for (SpecParam param : params) {
            String name=param.getName();
            queryBuilder.addAggregation(AggregationBuilders.terms(name).field("specs."+name+".keyword"));
        }
        //????????????
        AggregatedPage<Goods> result = template.queryForPage(queryBuilder.build(), Goods.class);
        //????????????
        Aggregations aggs = result.getAggregations();
        for (SpecParam param:params) {
            String name=param.getName();
            Terms terms=aggs.get(name);
            //??????map
            Map<String ,Object> map=new HashMap<>();
            map.put("k",name);
            map.put("options",terms.getBuckets().
                    stream().map(bucket -> bucket.getKey()).collect(Collectors.toList()));
            specs.add(map);
        }
        return specs;
    }

    private List<Brand> parseBrandAgg(LongTerms terms) { //??????????????????id  ???LongTerms
        try {
            List<Long> ids = terms.getBuckets()
                    .stream().map(bucket -> bucket.getKeyAsNumber().longValue()).collect(Collectors.toList());

            List<Brand> brands = brandClient.queryBrandByIds(ids);
            return brands;
        }catch (Exception e){
            log.error("??????????????????");
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
            log.error("??????????????????");
            return null;
        }
    }

    /**
     * ?????????spu???????????????goods
     * @param spu
     * @return
     */

    public Goods buildGoods(Spu spu){
        //????????????
        List<Category> categories = categoryClient.queryCategoryByIds(
                Arrays.asList(spu.getCid1(), spu.getCid2(), spu.getCid3()));
        if(CollectionUtils.isEmpty(categories))
            new LyException(ExceptionEnum.CATEGORY_NOT_FOUND);
        List<String> categoryNames=categories.stream().map(Category::getName).collect(Collectors.toList());

        //????????????
        Brand brand=brandClient.queryBrandById(spu.getBrandId());
        String brandName=brand.getName();
        //????????????
        List<Sku> skuList = goodsClient.querySkuBySpuId(spu.getId());
        if(CollectionUtils.isEmpty(skuList))
            new LyException(ExceptionEnum.SKU_NOT_FOUND);

        //???sku????????????  ????????????????????????
        List<Map<String,Object>> skus=new ArrayList<>();
        Set<Long> priceList=new HashSet<>();
        for (Sku sku : skuList) {
            Map<String,Object> map=new HashMap<>();
            map.put("id",sku.getId());
            map.put("title",sku.getTitle());
            map.put("price",sku.getPrice());
            map.put("image",StringUtils.substringBefore(sku.getImages(),","));
            skus.add(map);
            //????????????
            priceList.add(sku.getPrice());
        }
        //Set<Long> priceList = skuList.stream().map(Sku::getPrice).collect(Collectors.toSet());
        //????????????
        Map<String,Object> specs=new HashMap<>();
        List<SpecParam> params = specificationClient.queryParamList(spu.getCid3(), null, true);
        if(CollectionUtils.isEmpty(params))
            throw new LyException(ExceptionEnum.SPEC_PARAM_NOT_FOUND);
        SpuDetail spuDetail=goodsClient.querySpuDetailById(spu.getId());
        //??????????????????
        Map<String, String> genericSpec = JsonUtils.toMap(spuDetail.getGenericSpec(), String.class, String.class);
        //??????????????????
        Map<String, List<String>> specialSpec = JsonUtils
                .nativeRead(spuDetail.getSpecialSpec(), new TypeReference<Map<String, List<String>>>() {
        });
        // spec??? key?????????????????????key??????????????????
        for (SpecParam param : params) {
            String key=param.getName();

            if(param.getGeneric()){
                String value=genericSpec.get(param.getId().toString());
                //???????????????????????????
                if(param.getNumeric()){
                    //???????????????  ????????? ??????????????????
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




        goods.setAll(all); //???????????? ?????????????????????????????????????????? ?????? ?????? ????????????
        goods.setPrice(priceList);//??????sku???????????????
        goods.setSkus(JsonUtils.toString(skus));//??????sku?????????json??????
        goods.setSpecs(specs);//????????????????????????;

        return goods;
    }

    //?????????
    private String chooseSegment(String value, SpecParam p) {
        double val = NumberUtils.toDouble(value);
        String result = "??????";
        // ???????????????
        for (String segment : p.getSegments().split(",")) {
            String[] segs = segment.split("-");
            // ??????????????????
            double begin = NumberUtils.toDouble(segs[0]);
            double end = Double.MAX_VALUE;
            if(segs.length == 2){
                end = NumberUtils.toDouble(segs[1]);
            }
            // ????????????????????????
            if(val >= begin && val < end){
                if(segs.length == 1){
                    result = segs[0] + p.getUnit() + "??????";
                }else if(begin == 0){
                    result = segs[1] + p.getUnit() + "??????";
                }else{
                    result = segment + p.getUnit();
                }
                break;
            }
        }
        return result;
    }
}
