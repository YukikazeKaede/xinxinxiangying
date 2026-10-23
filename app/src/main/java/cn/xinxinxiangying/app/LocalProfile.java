package cn.xinxinxiangying.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;

/** Account-scoped profile data; selected pictures never leave application storage. */
public final class LocalProfile {
    private final Context context;
    final String key;
    public LocalProfile(Context context,boolean postgraduate){
        this.context=context.getApplicationContext();
        key=(postgraduate?"pg":"ug")+new SchoolAccount(context).scope(postgraduate);
    }
    public String name(){return context.getSharedPreferences("local_profiles_v1",0).getString(key,"");}
    public boolean saveName(String value){
        String name=value==null?"":value.trim();
        if(name.isEmpty()||name.length()>80||name.matches("(?s).*[\\p{Cntrl}].*"))return false;
        return context.getSharedPreferences("local_profiles_v1",0).edit().putString(key,name).commit();
    }
    private AtomicFile avatarFile(){return new AtomicFile(new File(new File(context.getFilesDir(),"profiles"),key+".png"));}
    public Bitmap avatar(){synchronized(LocalProfile.class){
        try(InputStream in=avatarFile().openRead()){return BitmapFactory.decodeStream(in);}catch(IOException e){return null;}
    }}
    public void saveAvatar(Uri uri)throws IOException{
        if(!"content".equals(uri.getScheme()))throw new IOException("Unsupported picture source");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
        if(options.outWidth<1||options.outHeight<1)throw new IOException("Unreadable picture");
        options.inSampleSize=1;
        while(options.outWidth/options.inSampleSize>512||options.outHeight/options.inSampleSize>512)options.inSampleSize*=2;
        options.inJustDecodeBounds=false;
        Bitmap bitmap;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}
        if(bitmap==null)throw new IOException("Unreadable picture");
        int orientation=ExifInterface.ORIENTATION_NORMAL;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(IOException ignored){}
        Matrix transform=new Matrix();
        switch(orientation){
            case 2:transform.setScale(-1,1);break;
            case 3:transform.setRotate(180);break;
            case 4:transform.setScale(1,-1);break;
            case 5:transform.setRotate(90);transform.postScale(-1,1);break;
            case 6:transform.setRotate(90);break;
            case 7:transform.setRotate(-90);transform.postScale(-1,1);break;
            case 8:transform.setRotate(-90);break;
        }
        if(!transform.isIdentity()){Bitmap upright=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),transform,true);bitmap.recycle();bitmap=upright;}
        AtomicFile file=avatarFile();
        try{synchronized(LocalProfile.class){
            FileOutputStream output=null;
            try{
            boolean pg="pg".equals(key.substring(0,2));
            if(!AppSession.valid(context)||pg!=context.getSharedPreferences("school_profile",0).getBoolean("postgraduate",false)||!key.equals(new LocalProfile(context,pg).key))throw new IOException("Account changed");
            File folder=file.getBaseFile().getParentFile();
            if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Cannot create profile directory");
            output=file.startWrite();
            // Re-encode to discard metadata and bound memory/disk use on subsequent loads.
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException("Cannot save picture");
            file.finishWrite(output);output=null;
            }finally{if(output!=null)file.failWrite(output);}
        }
        }finally{bitmap.recycle();}
    }
    public static synchronized void clear(Context context){
        context.getSharedPreferences("local_profiles_v1",0).edit().clear().commit();
        File[] files=new File(context.getFilesDir(),"profiles").listFiles();
        if(files!=null)for(File file:files)if(file.isFile())file.delete();
    }
}
