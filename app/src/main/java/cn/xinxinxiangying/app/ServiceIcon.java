package cn.xinxinxiangying.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/** Original, scalable color illustrations for the tools grid. */
public final class ServiceIcon extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String service;
    public ServiceIcon(Context context,String service){
        super(context);this.service=service;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private void fill(int color){paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(color);}
    private void rect(Canvas c,float l,float t,float r,float b,float radius,int color){fill(color);c.drawRoundRect(l,t,r,b,radius,radius,paint);}
    private void gradient(Canvas c,float l,float t,float r,float b,float radius,int top,int bottom){
        fill(top);paint.setShader(new LinearGradient(l,t,r,b,top,bottom,Shader.TileMode.CLAMP));c.drawRoundRect(l,t,r,b,radius,radius,paint);paint.setShader(null);
    }
    private void polygon(Canvas c,int color,float... points){
        fill(color);Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);path.close();c.drawPath(path,paint);
    }
    private void line(Canvas c,int color,float width,float... points){
        fill(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
    }
    private void books(Canvas c){
        rect(c,5,30,43,43,4,0xff5a83d9);rect(c,7,31,40,35,1,0xffdce9ff);
        rect(c,3,18,41,31,4,0xff88adeb);rect(c,6,19,39,23,1,0xffeef5ff);
        gradient(c,5,5,43,19,4,0xff6f9aef,0xff4e7edb);
        polygon(c,Color.WHITE,14,19,22,19,22,42,18,38,14,42);
        rect(c,25,10,36,12,1,0xffc4d9ff);
    }
    private void grades(Canvas c){
        gradient(c,7,3,41,44,5,0xffe7a6dc,0xffcf7dc7);
        polygon(c,0xffefc4e8,31,44,41,34,31,34);
        rect(c,12,12,25,15,1.5f,Color.WHITE);rect(c,12,23,25,26,1.5f,Color.WHITE);
        line(c,0xfffff5de,2.5f,30,12,32,14,36,9);
        line(c,0xffffe3de,2.5f,30,23,35,28);line(c,0xffffe3de,2.5f,35,23,30,28);
    }
    private void creditBook(Canvas c){
        polygon(c,0xff16a76b,4,7,23,14,23,44,4,37);
        polygon(c,0xffffb24f,25,14,44,7,44,37,25,44);
        polygon(c,0xff0e8d60,4,7,8,9,8,38,4,37);
        polygon(c,0xffec963d,40,9,44,7,44,37,40,39);
        line(c,0xfff5fff8,2.6f,11,20,17,22);line(c,0xfff5fff8,2.6f,11,28,17,30);
        line(c,0xfffffff5,2.6f,31,22,37,20);line(c,0xfffffff5,2.6f,31,30,37,28);
    }
    private void calendar(Canvas c){
        rect(c,5,8,43,43,5,0xffffecd9);rect(c,5,8,43,19,4,0xfff38e55);rect(c,5,15,43,19,0,0xfff38e55);
        rect(c,13,3,17,13,2,0xffde7947);rect(c,31,3,35,13,2,0xffde7947);
        for(int y=24;y<=32;y+=8)for(int x=12;x<=28;x+=8)rect(c,x,y,x+4,y+4,1,0xffeeab75);
        fill(0xff35aa7b);c.drawCircle(36,35,8,paint);line(c,Color.WHITE,2.3f,32,35,35,38,40,32);
    }
    private void card(Canvas c){
        gradient(c,3,9,45,40,4,0xff45a7f3,0xff087bd7);rect(c,3,13,45,20,0,0xffb8ddfa);rect(c,3,20,45,23,0,0xffe9f6ff);
        rect(c,8,30,17,34,1,Color.WHITE);rect(c,29,32,39,34,1,0xff72c0fa);
    }
    private void qr(Canvas c){
        for(int[] point:new int[][]{{5,5},{29,5},{5,29}}){
            int x=point[0],y=point[1];rect(c,x,y,x+14,y+14,3,0xff8b73cf);rect(c,x+3,y+3,x+11,y+11,1,Color.WHITE);rect(c,x+5,y+5,x+9,y+9,1,0xff8b73cf);
        }
        rect(c,23,6,26,16,1,0xffb196df);rect(c,23,21,32,25,1,0xff8b73cf);
        rect(c,23,29,27,39,1,0xff8b73cf);rect(c,31,29,42,33,1,0xffb196df);rect(c,31,37,35,43,1,0xff8b73cf);rect(c,39,36,43,43,1,0xff8b73cf);
    }
    private void electricity(Canvas c){
        polygon(c,0xffffc04a,28,2,8,28,21,28,18,46,41,17,27,17);
        polygon(c,0xfff5a129,28,2,21,23,34,23,18,46,41,17,27,17);
    }
    private void account(Canvas c){
        gradient(c,5,5,43,43,5,0xff77c5b0,0xff319b82);rect(c,15,2,33,8,2,0xff2a8a73);
        fill(0xfff1fff8);c.drawCircle(20,18,5,paint);c.drawRoundRect(11,26,29,36,5,5,paint);
        rect(c,33,16,38,19,1,0xffc1ecdf);rect(c,33,23,38,26,1,0xffc1ecdf);rect(c,33,30,38,33,1,0xffc1ecdf);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.save();float scale=Math.min(getWidth(),getHeight())/48f;
        canvas.translate((getWidth()-48*scale)/2,(getHeight()-48*scale)/2);canvas.scale(scale,scale);
        if("exams".equals(service))books(canvas);
        else if("gpa".equals(service))grades(canvas);
        else if("second".equals(service))creditBook(canvas);
        else if("activities".equals(service))calendar(canvas);
        else if("card".equals(service))card(canvas);
        else if("code".equals(service))qr(canvas);
        else if("electricity".equals(service))electricity(canvas);
        else if("account".equals(service))account(canvas);
        canvas.restore();
    }
}
