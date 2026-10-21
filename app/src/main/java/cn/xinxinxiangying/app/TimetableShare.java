package cn.xinxinxiangying.app;

import android.app.Activity;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.UUID;

/** Only app-created share files, with temporary per-URI read grants. */
public final class TimetableShare extends ContentProvider {
    public static void send(Activity a,byte[] bytes,String extension,String mime)throws IOException{
        File dir=new File(a.getCacheDir(),"shared_timetables");if(!dir.exists()&&!dir.mkdirs())throw new IOException("分享目录无法创建");String name=UUID.randomUUID().toString()+extension;File file=new File(dir,name);try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}
        Uri uri=new Uri.Builder().scheme("content").authority(a.getPackageName()+".share").appendPath(name).build();Intent send=new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newUri(a.getContentResolver(),"课表",uri));a.startActivity(Intent.createChooser(send,"分享课表"));
    }
    @Override public boolean onCreate(){return true;}
    private File file(Uri uri)throws FileNotFoundException{String name=uri.getLastPathSegment();if(name==null||!name.matches("[0-9a-f-]{36}\\.(ics|json|png)"))throw new FileNotFoundException();File file=new File(new File(getContext().getCacheDir(),"shared_timetables"),name);if(!file.isFile())throw new FileNotFoundException();return file;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public String getType(Uri uri){String name=uri.getLastPathSegment();return name!=null&&name.endsWith(".ics")?"text/calendar":name!=null&&name.endsWith(".png")?"image/png":"application/json";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){try{File file=file(uri);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(cols);Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]="心心相应课表"+file.getName().substring(36);else if(OpenableColumns.SIZE.equals(cols[i]))row[i]=file.length();}cursor.addRow(row);return cursor;}catch(FileNotFoundException e){return null;}}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
