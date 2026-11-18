package com.example.picturebackend.manager;

import com.example.picturebackend.exception.*;
import com.example.picturebackend.model.tdo.file.UploadPictureResult;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Content-addressed storage. URLs always go through the authorized media controller. */
@Service
public class FileManager {
    @Value("${app.upload-dir:./data/uploads}") private String uploadDir;
    @Value("${app.storage:local}") private String storage;
    @Autowired(required=false) private CosManager cos;

    public UploadPictureResult uploadPicture(MultipartFile file, String ignoredPrefix) {
        validPicture(file);
        try {
            byte[] bytes=file.getBytes(); BufferedImage image; String format;
            try (ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
                ThrowUtils.throwIf(!readers.hasNext(), ErrorCode.PARAMS_ERROR, "不支持的图片格式");
                ImageReader reader=readers.next();
                try {
                    reader.setInput(input); int w=reader.getWidth(0), h=reader.getHeight(0);
                    ThrowUtils.throwIf(w<=0 || h<=0 || (long)w*h>25000000, ErrorCode.PARAMS_ERROR, "图片不能超过 2500 万像素");
                    format=reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (format.equals("jpeg")) format="jpg";
                    ThrowUtils.throwIf(!Arrays.asList("jpg","png","webp").contains(format), ErrorCode.PARAMS_ERROR, "仅支持 JPG、PNG、WebP");
                    image=reader.read(0);
                } finally { reader.dispose(); }
            }
            StringBuilder hash=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes)) hash.append(String.format("%02x",b&255));
            String key=hash+"."+format; write(key,bytes);
            int w=image.getWidth(), h=image.getHeight(); double scale=Math.min(1d,480d/Math.max(w,h));
            BufferedImage thumb=new BufferedImage(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),BufferedImage.TYPE_INT_RGB);
            Graphics2D g=thumb.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0,0,thumb.getWidth(),thumb.getHeight());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image,0,0,thumb.getWidth(),thumb.getHeight(),null);g.dispose();
            ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(thumb,"jpg",out);write(key+".thumb.jpg",out.toByteArray());
            UploadPictureResult result=new UploadPictureResult();
            String name=Optional.ofNullable(file.getOriginalFilename()).orElse("图片").replace('\\','/');
            name=name.substring(name.lastIndexOf('/')+1).replaceFirst("\\.[^.]+$","");
            result.setPicName(name.substring(0,Math.min(128,name.length())));
            result.setUrl("/api/media/"+key);result.setPicSize((long)bytes.length);
            result.setPicWidth(w);result.setPicHeight(h);result.setPicScale((double)w/h);result.setPicFormat(format);
            result.setFileHash(hash.toString()); long red=0,green=0,blue=0,n=0;
            for(int y=0;y<h;y+=Math.max(1,h/80))for(int x=0;x<w;x+=Math.max(1,w/80)){
                Color c=new Color(image.getRGB(x,y));red+=c.getRed();green+=c.getGreen();blue+=c.getBlue();n++;
            }
            result.setPicColor(String.format("#%02x%02x%02x",red/n,green/n,blue/n));
            BufferedImage small=new BufferedImage(9,8,BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D sg=small.createGraphics();sg.drawImage(image,0,0,9,8,null);sg.dispose();
            long dh=0; for(int y=0;y<8;y++)for(int x=0;x<8;x++) dh=(dh<<1)|((small.getRGB(x,y)&255)>(small.getRGB(x+1,y)&255)?1:0);
            result.setPerceptualHash(String.format("%016x",dh)); return result;
        } catch (BusinessException e) { throw e; }
        catch(Exception e){throw new BusinessException(ErrorCode.OPERATION_ERROR,"图片读取或存储失败");}
    }
    public void validPicture(MultipartFile file){
        ThrowUtils.throwIf(file==null || file.isEmpty() || file.getSize()>2L*1024*1024,ErrorCode.PARAMS_ERROR,"请选择不超过 2MB 的图片");
    }
    private Path root() throws IOException {Path p=Paths.get(uploadDir).toAbsolutePath().normalize();Files.createDirectories(p);return p;}
    private String objectKey(String key){return "gallery/"+key.substring(0,2)+"/"+key.substring(2,4)+"/"+key;}
    private Path objectPath(Path root,String key)throws IOException{Path folder=root.resolve(key.substring(0,2)).resolve(key.substring(2,4));Files.createDirectories(folder);return folder.resolve(key);}
    private void write(String key,byte[] bytes)throws IOException{
        if("cos".equals(storage)){
            ThrowUtils.throwIf(cos==null,ErrorCode.SYSTEM_ERROR,"COS 未配置");
            Path temp=Files.createTempFile("picture-",".bin");
            try{Files.write(temp,bytes);cos.putPictureObject(objectKey(key),temp.toFile());}finally{Files.deleteIfExists(temp);}
        }else{
            Path dest=objectPath(root(),key);
            if(!Files.exists(dest))try{Files.write(dest,bytes,StandardOpenOption.CREATE_NEW);}catch(FileAlreadyExistsException ignored){}
        }
    }
    public byte[] read(String key)throws IOException{
        ThrowUtils.throwIf(!key.matches("[a-f0-9]{64}\\.(jpg|png|webp)(\\.thumb\\.jpg)?"),ErrorCode.PARAMS_ERROR);
        if("cos".equals(storage))try(com.qcloud.cos.model.COSObject object=cos.getObject(objectKey(key));InputStream in=object.getObjectContent()){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int size;
            while((size=in.read(buffer))!=-1)out.write(buffer,0,size);return out.toByteArray();
        }
        return Files.readAllBytes(objectPath(root(),key));
    }
    public byte[] transform(byte[] source,int rotation,boolean flip,String format){
        try{
            ThrowUtils.throwIf(rotation%90!=0||Math.abs(rotation)>360,ErrorCode.PARAMS_ERROR);
            BufferedImage image=ImageIO.read(new ByteArrayInputStream(source));ThrowUtils.throwIf(image==null,ErrorCode.PARAMS_ERROR);
            int turns=((rotation%360)+360)%360;boolean swap=turns==90||turns==270;
            BufferedImage out=new BufferedImage(swap?image.getHeight():image.getWidth(),swap?image.getWidth():image.getHeight(),
                    "jpg".equals(format)?BufferedImage.TYPE_INT_RGB:BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=out.createGraphics();
            if("jpg".equals(format)){g.setColor(Color.WHITE);g.fillRect(0,0,out.getWidth(),out.getHeight());}
            g.translate(out.getWidth()/2.0,out.getHeight()/2.0);g.rotate(Math.toRadians(turns));g.scale(flip?-1:1,1);
            g.drawImage(image,-image.getWidth()/2,-image.getHeight()/2,null);g.dispose();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();ThrowUtils.throwIf(!ImageIO.write(out,format.equals("jpg")?"jpeg":format,bytes),ErrorCode.PARAMS_ERROR,"图片格式不支持编辑");return bytes.toByteArray();
        }catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException(ErrorCode.OPERATION_ERROR,"图片变换失败");}
    }
}
