package com.example.picturebackend.manager;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.Files;
public class ByteArrayMultipartFile implements MultipartFile {
    private final byte[] data; private final String name;
    public ByteArrayMultipartFile(byte[] data,String name){this.data=data;this.name=name;}
    public String getName(){return "file";} public String getOriginalFilename(){return name;}
    public String getContentType(){return "application/octet-stream";} public boolean isEmpty(){return data.length==0;}
    public long getSize(){return data.length;} public byte[] getBytes(){return data;}
    public InputStream getInputStream(){return new ByteArrayInputStream(data);}
    public void transferTo(File file)throws IOException{Files.write(file.toPath(),data);}
}
