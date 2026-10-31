package com.snowread.app;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
/** Bounded font payloads; paths and content hashes are verified before directory commit. */
final class PortableFonts {
    static final int MAX=32,LIMIT=20*1024*1024;
    static LinkedHashMap<String,File> files(File library)throws IOException {
        LinkedHashMap<String,File> found=new LinkedHashMap<>();
        for(File folder:new File[]{new File(library,"fonts"),new File(library.getParentFile(),"user-fonts")}){
            File[] list=folder.listFiles();if(list==null)continue;
            for(File f:list)if(f.isFile()&&f.getName().matches("[a-f0-9]{64}\\.font"))found.put(f.getName(),f);
        }
        if(found.size()>MAX)throw new IOException("自定义字体超过32款");return found;
    }
    static void validate(File file,String name)throws Exception {
        if(!name.matches("[a-f0-9]{64}\\.font")||file.length()<12||file.length()>LIMIT)throw new IOException("备份字体无效");
        try(DataInputStream in=new DataInputStream(new FileInputStream(file))){int magic=in.readInt();if(magic!=0x00010000&&magic!=0x4f54544f&&magic!=0x74727565)throw new IOException("备份字体格式无效");}
        MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)hash.update(b,0,n);}
        StringBuilder id=new StringBuilder();for(byte b:hash.digest())id.append(String.format(Locale.ROOT,"%02x",b&255));if(!name.equals(id+".font"))throw new IOException("备份字体校验失败");
    }
}
