package com.snowread.app;

import android.content.Context;
import android.net.Uri;
import android.graphics.*;
import android.media.ExifInterface;
import android.util.AtomicFile;
import java.io.*;

/** Import a local image, honor EXIF orientation and persist a bounded portrait thumbnail. */
final class CoverStore {
    private static final long MAX_IMAGE_BYTES=20L*1024*1024;
    private final Context context;
    private final File directory;
    CoverStore(Context context) throws IOException {
        this.context=context.getApplicationContext();
        directory=new File(new File(context.getFilesDir(),"library"),"covers");
        if(!directory.exists()&&!directory.mkdirs()) throw new IOException("无法创建封面目录");
    }
    private File file(String id) {
        if(id==null||!id.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid book identity");
        return new File(directory,id+".jpg");
    }
    boolean has(String id) {return file(id).exists();}
    void remove(String id) {new AtomicFile(file(id)).delete();}
    Bitmap thumbnail(String id) {
        if(!has(id)) return null;
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream in=new AtomicFile(file(id)).openRead()){BitmapFactory.decodeStream(in,null,bounds);}catch(IOException e){return null;}
        if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>40000||bounds.outHeight>40000)return null;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=CoverGeometry.sampleSize(bounds.outWidth,bounds.outHeight,360);
        try(InputStream in=new AtomicFile(file(id)).openRead()) {return BitmapFactory.decodeStream(in,null,options);}
        catch(IOException e) {return null;}
    }
    void importImage(String id,Uri uri) throws Exception {
        File temporary=File.createTempFile("snowread-cover-",".image",context.getCacheDir());
        Bitmap decoded=null,oriented=null,portrait=null;
        try {
            try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temporary)) {
                if(in==null) throw new IOException("无法读取这张图片，请重新选择");
                byte[] buffer=new byte[16384];long total=0;int count;
                while((count=in.read(buffer))!=-1) {
                    total+=count;if(total>MAX_IMAGE_BYTES) throw new IOException("请选择不超过 20 MB 的封面图片");
                    out.write(buffer,0,count);
                }
                if(total==0) throw new IOException("这张图片是空文件");
            }
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(temporary.getAbsolutePath(),bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>40000||bounds.outHeight>40000)
                throw new IOException("图片无法识别或尺寸过大，请尝试另一张 JPEG 或 PNG");
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inSampleSize=CoverGeometry.sampleSize(bounds.outWidth,bounds.outHeight,1600);
            options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            decoded=BitmapFactory.decodeFile(temporary.getAbsolutePath(),options);
            if(decoded==null) throw new IOException("无法解码这张图片，请尝试 JPEG 或 PNG");
            int orientation=ExifInterface.ORIENTATION_NORMAL;
            try {orientation=new ExifInterface(temporary.getAbsolutePath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}
            catch(IOException ignored) {}
            Matrix transform=orientationMatrix(orientation);
            oriented=transform==null?decoded:Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),transform,true);
            portrait=Bitmap.createBitmap(480,720,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(portrait);canvas.drawColor(Color.WHITE);
            float[] crop=CoverGeometry.centerCrop(oriented.getWidth(),oriented.getHeight(),480,720);
            Rect source=new Rect(Math.max(0,(int)Math.floor(crop[0])),Math.max(0,(int)Math.floor(crop[1])),
                Math.min(oriented.getWidth(),(int)Math.ceil(crop[2])),Math.min(oriented.getHeight(),(int)Math.ceil(crop[3])));
            canvas.drawBitmap(oriented,source,new Rect(0,0,480,720),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
            AtomicFile destination=new AtomicFile(file(id));FileOutputStream out=destination.startWrite();
            try {
                if(!portrait.compress(Bitmap.CompressFormat.JPEG,88,out)) throw new IOException("封面保存失败");
                destination.finishWrite(out);
            } catch(Exception error) {destination.failWrite(out);throw error;}
        } finally {
            if(portrait!=null) portrait.recycle();
            if(oriented!=null&&oriented!=decoded) oriented.recycle();
            if(decoded!=null) decoded.recycle();
            temporary.delete();
        }
    }
    private Matrix orientationMatrix(int orientation) {
        float[] values;
        switch(orientation) {
            case 2:values=new float[]{-1,0,0,0,1,0,0,0,1};break;
            case 3:values=new float[]{-1,0,0,0,-1,0,0,0,1};break;
            case 4:values=new float[]{1,0,0,0,-1,0,0,0,1};break;
            case 5:values=new float[]{0,1,0,1,0,0,0,0,1};break;
            case 6:values=new float[]{0,-1,0,1,0,0,0,0,1};break;
            case 7:values=new float[]{0,-1,0,-1,0,0,0,0,1};break;
            case 8:values=new float[]{0,1,0,-1,0,0,0,0,1};break;
            default:return null;
        }
        Matrix result=new Matrix();result.setValues(values);return result;
    }
}
