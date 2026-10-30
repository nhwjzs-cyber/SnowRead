package com.snowread.app;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Private, bounded copies. Font identifiers never contain a provider URI or a path. */
final class FontLibrary {
    private static final LinkedHashMap<String,Typeface> cache=new LinkedHashMap<String,Typeface>(8,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Typeface> e){return size()>8;}};
    private static final HashSet<String> missing=new HashSet<>();
    static boolean validId(String id){return id!=null&&(id.isEmpty()||id.matches("[a-f0-9]{64}"));}
    private static File home(Context c){return new File(c.getFilesDir(),"user-fonts");}
    static synchronized String name(Context c,String id){return c.getSharedPreferences("font-library",0).getString(id,"自定义字体")+(missing.contains(id)?"（不可用，已回退）":"");}
    static ArrayList<String> ids(Context c){ArrayList<String> result=new ArrayList<>();File[] files=home(c).listFiles();if(files!=null)for(File f:files){String n=f.getName();if(n.endsWith(".font")){String id=n.substring(0,n.length()-5);if(validId(id)&&!id.isEmpty())result.add(id);}}Collections.sort(result,(a,b)->name(c,a).compareToIgnoreCase(name(c,b)));return result;}
    static synchronized Typeface face(Context c,Typeset t){
        if(!t.customFont.isEmpty()&&validId(t.customFont)){
            Typeface known=cache.get(t.customFont);if(known!=null)return known;
            if(!missing.contains(t.customFont)){File f=new File(home(c),t.customFont+".font");if(f.isFile())try{Typeface face=Typeface.createFromFile(f);cache.put(t.customFont,face);return face;}catch(RuntimeException ignored){}missing.add(t.customFont);}
        }
        return t.fontFamily==1?Typeface.SANS_SERIF:t.fontFamily==2?Typeface.DEFAULT:BundledFont.serif(c);
    }
    static String importFont(Context c,Uri uri,String filename)throws Exception{
        String lower=filename.toLowerCase(Locale.ROOT);if(!lower.endsWith(".ttf")&&!lower.endsWith(".otf"))throw new IOException("请选择 TTF 或 OTF 字体文件");
        File folder=home(c);if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法创建字体目录");
        File temp=File.createTempFile("font-import-",".tmp",c.getCacheDir());
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;
            try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){if(in==null)throw new IOException("无法读取字体");byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){total+=n;if(total>20L*1024*1024)throw new IOException("字体不能超过 20 MB");digest.update(b,0,n);out.write(b,0,n);}}
            if(total==0)throw new IOException("字体文件为空");
            try(DataInputStream head=new DataInputStream(new FileInputStream(temp))){int magic=head.readInt();if(magic!=0x00010000&&magic!=0x4f54544f&&magic!=0x74727565)throw new IOException("文件不是可用的 TTF / OTF 字体");}catch(EOFException e){throw new IOException("字体文件不完整",e);}
            try{Typeface.createFromFile(temp);}catch(RuntimeException e){throw new IOException("字体无法识别，请尝试另一款 TTF / OTF 字体",e);}
            StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));String id=hex.toString();if(!new File(folder,id+".font").isFile()&&ids(c).size()>=32)throw new IOException("最多保留32款自定义字体，请先移除不使用的字体");
            AtomicFile target=new AtomicFile(new File(folder,id+".font"));FileOutputStream out=target.startWrite();try(InputStream in=new FileInputStream(temp)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);target.finishWrite(out);}catch(Exception e){target.failWrite(out);throw e;}
            synchronized(FontLibrary.class){missing.remove(id);cache.put(id,Typeface.createFromFile(new File(folder,id+".font")));}
            c.getSharedPreferences("font-library",0).edit().putString(id,filename.length()>120?filename.substring(0,120):filename).apply();return id;
        }finally{temp.delete();}
    }
    static synchronized void remove(Context c,String id)throws IOException{if(!validId(id)||id.isEmpty())throw new IOException("字体标识无效");File f=new File(home(c),id+".font");new AtomicFile(f).delete();if(f.exists())throw new IOException("无法移除字体");cache.remove(id);missing.add(id);c.getSharedPreferences("font-library",0).edit().remove(id).apply();}
}
