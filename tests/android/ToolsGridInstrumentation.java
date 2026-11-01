package cn.xinxinxiangying.app.test;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import cn.xinxinxiangying.app.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Six service icons and task overlays on an isolated synthetic installation. */
public final class ToolsGridInstrumentation extends Instrumentation {
    private int checks; private MainActivity main;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private View text(View root,String label){
        if(root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=text(((ViewGroup)root).getChildAt(i),label);if(found!=null)return found;}
        return null;
    }
    private void icons(View root,List<View> out){
        if(root instanceof ServiceIcon)out.add(root);
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)icons(((ViewGroup)root).getChildAt(i),out);
    }
    @Override public void onStart(){
        Context context=getTargetContext();Bundle report=new Bundle();boolean owned=false;int result=Activity.RESULT_OK;
        try{
            check(context.getPackageName().endsWith(".pr23test"),"isolated package only");owned=true;
            new SchoolAccount(context).clear();context.getSharedPreferences("school_profile",0).edit().putBoolean("postgraduate",false).commit();
            new SchoolAccount(context).save(false,"SYNTHETIC_TOOL_GRID","",false);
            context.getSharedPreferences("school_session",0).edit().putString("identity","ug:SYNTHETIC_TOOL_GRID").commit();
            Field update=UpdateChecker.class.getDeclaredField("autoChecked");update.setAccessible(true);update.setBoolean(null,true);
            main=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            runOnMainSync(()->{
                try{Field preload=MainActivity.class.getDeclaredField("paymentPreloadAttempted");preload.setAccessible(true);preload.setBoolean(main,true);}catch(Exception e){throw new RuntimeException(e);}
                main.selectTab(1);
            });waitForIdleSync();
            View root=main.getWindow().getDecorView();
            for(String label:new String[]{"付款码","考试安排","校园卡","宿舍电费","学分绩点","二课报名","第二课堂"})check(text(root,label)!=null,"required tools label");
            for(String label:new String[]{"学分情况","学校账号","活动报名"})check(text(root,label)==null,"removed grid entry");
            List<View> grid=new ArrayList<>();icons(root,grid);check(grid.size()==6,"exactly six service icons");
            check(grid.get(0).getParent().getParent()==grid.get(2).getParent().getParent(),"first row has three services");
            check(grid.get(3).getParent().getParent()==grid.get(5).getParent().getParent(),"second row has three services");
            check(grid.get(0).getParent().getParent()!=grid.get(3).getParent().getParent(),"services occupy two rows");
            for(View icon:grid)check(icon.getWidth()>0&&icon.getHeight()>0,"service icon has layout bounds");
            final Bitmap[] screenshot={null};
            runOnMainSync(()->{screenshot[0]=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(screenshot[0]));});
            File output=new File(context.getExternalFilesDir(null),"SYNTHETIC-tools-six.png");
            try(OutputStream out=new FileOutputStream(output)){screenshot[0].compress(Bitmap.CompressFormat.PNG,100,out);}screenshot[0].recycle();
            report.putString("screenshot",output.toString());
            report.putString("result","PASS ToolsGridInstrumentation: "+checks+" checks");
        }catch(Throwable error){result=Activity.RESULT_CANCELED;report.putString("result","FAIL "+android.util.Log.getStackTraceString(error));}
        finally{if(main!=null)runOnMainSync(()->main.finish());if(owned)new SchoolAccount(context).clear();}
        finish(result,report);
    }
}
