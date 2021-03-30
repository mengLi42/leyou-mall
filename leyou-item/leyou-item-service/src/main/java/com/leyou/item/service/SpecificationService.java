package com.leyou.item.service;

import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.item.mapper.SpecGroupMapper;
import com.leyou.item.mapper.SpecParamMapper;
import com.leyou.item.pojo.SpecGroup;
import com.leyou.item.pojo.SpecParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SpecificationService {
    @Autowired
    private SpecGroupMapper groupMapper;
    @Autowired
    private SpecParamMapper specParamMapper;
    public List<SpecGroup> queryGroupByCid(Long cid) {
        SpecGroup g=new SpecGroup();
        g.setCid(cid);
        List<SpecGroup> list= groupMapper.select(g);
        if(CollectionUtils.isEmpty(list)){
            throw new LyException(ExceptionEnum.SPEC_GROUP_NOT_FOUND);
        }
        return list;
    }



    public List<SpecParam> queryParamParamList(Long gid, Long cid, Boolean searching) {
        SpecParam param=new SpecParam();
        param.setGroupId(gid);
        param.setSearching(searching);
        param.setCid(cid);
        param.setSearching(searching);
        List<SpecParam> list=specParamMapper.select(param);
        if(CollectionUtils.isEmpty(list)){
            throw new LyException(ExceptionEnum.SPEC_PARAM_NOT_FOUND);
        }
        return list;

    }

    public List<SpecGroup> queryGroupAndValuByCid(Long cid) {
        List<SpecGroup> specGroups = queryGroupByCid(cid);
        //查询组内参数   多次发起查询性能不好，因此一次性查询分类下的所有参数
        List<SpecParam> specParams = queryParamParamList(null, cid, null);
        //先把规格参数变成map key为组id value为组内的所有参数  避免使用双重for
        Map<Long,List<SpecParam> > map=new HashMap<>();
        for(SpecParam param:specParams){
            if(!map.containsKey(param.getGroupId())){
                //组ID在map中不存在
                map.put(param.getGroupId(),new ArrayList<>());
            }
            map.get(param.getGroupId()).add(param);
        }

        for (SpecGroup specGroup : specGroups) {
            specGroup.setParams(map.get(specGroup.getId()));
        }
        return null;
    }
}
