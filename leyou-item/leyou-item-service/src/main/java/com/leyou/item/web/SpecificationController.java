package com.leyou.item.web;

import com.leyou.item.pojo.SpecGroup;
import com.leyou.item.pojo.SpecParam;
import com.leyou.item.service.SpecificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("spec")
public class SpecificationController {
    @Autowired
    private SpecificationService specificationService;
    @GetMapping("groups/{cid}")
    public ResponseEntity<List<SpecGroup>> queryGroupByCid(@PathVariable("cid")Long cid){
        return ResponseEntity.ok(specificationService.queryGroupByCid(cid));
    }

    /**
     * 根据组id或者cid 查询参数的集合
     * @param gid
     * @param cid
     * @param searching
     * @return
     */
    @GetMapping("params")
    public ResponseEntity<List<SpecParam>> queryParamList(
            @RequestParam(value = "cid",required = false)Long cid,//设置为false则参数可有可无，否则两个参数都必须有
            @RequestParam(value = "gid",required = false)Long gid,
            @RequestParam(value = "searching",required = false)Boolean searching){//为以后搜索做准备

        return ResponseEntity.ok(specificationService.queryParamParamList(gid,cid,searching));
    }
    /**
     * 根据分类查询规格组和组内参数
     */
    @GetMapping("group")
    public ResponseEntity<List<SpecGroup>> queryGroupAndValueByCid(@RequestParam("cid")Long cid){
        return ResponseEntity.ok(specificationService.queryGroupAndValuByCid(cid));
    }
}
