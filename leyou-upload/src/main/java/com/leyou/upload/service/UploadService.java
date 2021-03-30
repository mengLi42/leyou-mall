package com.leyou.upload.service;

import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class UploadService {
    private static final  List<String> ALLOWTYPES= Arrays.asList("image/jpeg","image/png","image/bmp");
    public String uploadIamge(MultipartFile file) {

        //保存文件到本地
        //this.getClass().getClassLoader().getResource("").getFile();
        File dest=new File("D:\\IdeaProjects\\leyou\\uploadfiles",file.getOriginalFilename());
        try {
        //校验文件类型
            String contentType = file.getContentType();
            if(!ALLOWTYPES.contains(contentType)){
                throw new LyException(ExceptionEnum.INVALID_FILE_FORMAT);
            }
        //校验文件内容
            BufferedImage image= ImageIO.read(file.getInputStream());
            if(image==null){
                throw  new  LyException(ExceptionEnum.INVALID_FILE_FORMAT);
            }
            file.transferTo(dest);
            return "http://image.leyou.com/upload/"+file.getOriginalFilename();
        } catch (IOException e) {
           //上传失败 写日志 抛异常
           log.error("上传图片失败",e);
           throw new LyException(ExceptionEnum.UPLOAD_IMAGE_EXCEPTION);
        }
        //返回路径

    }
}
