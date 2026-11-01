package cn.xinxinxiangying.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/** Scalable illustrations redrawn for the six campus services. */
public final class ServiceIcon extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String service;
    public ServiceIcon(Context context, String service) {
        super(context); this.service = service;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private void fill(int color) {
        paint.setShader(null); paint.clearShadowLayer(); paint.setStyle(Paint.Style.FILL); paint.setColor(color);
    }
    private void shadow() { paint.setShadowLayer(1.6f, 0, 1.1f, 0x25294336); }
    private void rect(Canvas c, float l, float t, float r, float b, float radius, int color) {
        fill(color); c.drawRoundRect(l,t,r,b,radius,radius,paint);
    }
    private void gradient(Canvas c, float l, float t, float r, float b, float radius, int top, int bottom) {
        fill(top); shadow(); paint.setShader(new LinearGradient(l,t,r,b,top,bottom,Shader.TileMode.CLAMP));
        c.drawRoundRect(l,t,r,b,radius,radius,paint); paint.setShader(null); paint.clearShadowLayer();
    }
    private void polygon(Canvas c, int color, float... points) {
        fill(color); Path path = new Path(); path.moveTo(points[0],points[1]);
        for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);
        path.close(); c.drawPath(path,paint);
    }
    private void line(Canvas c, int color, float width, float... points) {
        fill(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        Path path = new Path(); path.moveTo(points[0],points[1]);
        for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);
        c.drawPath(path,paint); paint.setStyle(Paint.Style.FILL);
    }
    private void scan(Canvas c) {
        int green=0xff21a65a;
        line(c,green,3,8,20,8,8,20,8); line(c,green,3,44,8,56,8,56,20);
        line(c,green,3,8,44,8,56,20,56); line(c,green,3,44,56,56,56,56,44);
        for(int[] dot:new int[][]{{16,16},{25,13},{37,16},{47,15},{14,26},{49,26},
                {16,36},{48,37},{14,46},{24,50},{35,52},{47,47},{19,49},{52,32}})
            rect(c,dot[0],dot[1],dot[0]+2.6f,dot[1]+2.6f,0,0xff8fd69c);
        fill(green); shadow(); c.drawCircle(32,32,12,paint);
        fill(Color.WHITE); paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(22); c.drawText("¥",32,40,paint);
    }
    private void exam(Canvas c) {
        gradient(c,14,12,52,53,6,0xffd3e8ff,0xffbfdcff);
        rect(c,14,12,52,25,6,0xff397deb); rect(c,14,19,52,25,0,0xff397deb);
        rect(c,14,24,52,26,0,0xffeff7ff);
        line(c,Color.WHITE,3,23,9,23,16); line(c,Color.WHITE,3,43,9,43,16);
        line(c,0xff377ce7,4.1f,25,38,31,44,42,32);
    }
    private void card(Canvas c) {
        gradient(c,8,16,56,49,3,0xff358e99,0xff1f7f89);
        rect(c,8,21,56,26,0,0xff6bb6bc);
        rect(c,13,38,19,43,1,0xfff3ffff); line(c,0xff83c4ca,1.3f,42,40,50,40);
    }
    private void electricity(Canvas c) {
        rect(c,46,12,53,25,1,0xffab6908); rect(c,45,10,55,14,1,0xffab6908);
        fill(0xffba801c); shadow(); Path house = new Path();
        house.moveTo(14,31); house.lineTo(32,18); house.lineTo(50,31); house.lineTo(50,55);
        house.quadTo(50,59,46,59); house.lineTo(18,59); house.quadTo(14,59,14,55); house.close();
        paint.setShader(new LinearGradient(14,22,50,59,0xffcf952e,0xffb77b15,Shader.TileMode.CLAMP));
        c.drawPath(house,paint);
        line(c,0xffaa6605,4.4f,6,32,32,12,58,32);
        polygon(c,0xfffff8df,35,29,25,43,31,43,29,53,40,38,34,38);
    }
    private void gpa(Canvas c) {
        gradient(c,11,35,23,54,2.5f,0xffcfb9ee,0xffbfa4e5);
        gradient(c,28,23,40,54,2.5f,0xffa57cdd,0xff9368cf);
        gradient(c,45,12,57,54,2.5f,0xff8050bc,0xff6a3aaa);
        line(c,0xffd6c2f0,3.8f,8,56,60,56);
        line(c,0xff6b3aae,2.4f,47,5,50,8,56,2);
    }
    private void ticket(Canvas c) {
        gradient(c,6,19,58,49,5.5f,0xfff47604,0xffdc5900);
        fill(Color.WHITE); c.drawCircle(42,19,4.5f,paint); c.drawCircle(42,49,4.5f,paint);
        for(int y=27;y<43;y+=5)line(c,0xfff29b4b,1,42,y,42,y+1.5f);
        fill(Color.WHITE); Path star = new Path();
        for(int i=0;i<10;i++) {
            double angle=-Math.PI/2+i*Math.PI/5; float radius=i%2==0?8:3.8f;
            float x=22+(float)Math.cos(angle)*radius,y=34+(float)Math.sin(angle)*radius;
            if(i==0)star.moveTo(x,y);else star.lineTo(x,y);
        }
        star.close(); c.drawPath(star,paint);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.save(); float scale=Math.min(getWidth(),getHeight())/64f;
        canvas.translate((getWidth()-64*scale)/2,(getHeight()-64*scale)/2); canvas.scale(scale,scale);
        if("code".equals(service))scan(canvas);
        else if("exams".equals(service))exam(canvas);
        else if("card".equals(service))card(canvas);
        else if("electricity".equals(service))electricity(canvas);
        else if("gpa".equals(service))gpa(canvas);
        else if("activities".equals(service))ticket(canvas);
        canvas.restore();
    }
}
