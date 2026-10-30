package com.sasakiy84.solovlog;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.widget.VideoView;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.BaseGlShaderProgram;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.GlShaderProgram;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

/** Compact text masks share layout with previews; export ink is chosen from every video frame. */
@UnstableApi
final class CaptionOverlay implements GlEffect {
    private final Clip clip;
    private final String day;
    private final boolean showTime;
    CaptionOverlay(Clip clip,String day,boolean showTime){this.clip=clip;this.day=day;this.showTime=showTime;}
    @Override public GlShaderProgram toGlShaderProgram(Context context,boolean useHdr) throws VideoFrameProcessingException{
        return new Program(context,useHdr,clip,day,showTime);
    }
    private record Label(Bitmap mask,float x,float y){}
    private static Label[] labels(int w,int h,Clip clip,String day,boolean showTime){
        float edge=Math.min(w,h),marginX=w*.05f,marginY=h*.05f;
        Label clock=null,heading=null,caption=null;
        float available=w*.9f;
        if(showTime){
            Bitmap mask=mask(JournalRules.captureTime(clip),edge*.034f,Math.round(available),1);
            clock=new Label(mask,w-marginX-mask.getWidth(),h-marginY-mask.getHeight());
            available-=mask.getWidth()+edge*.04f;
        }
        if(!day.isBlank())heading=new Label(mask(day,edge*.038f,Math.round(w*.9f),3),marginX,marginY);
        if(!clip.caption().isBlank()){
            Bitmap mask=mask(clip.caption(),edge*.04f,Math.max(1,Math.round(available)),6);
            caption=new Label(mask,marginX,h-marginY-mask.getHeight());
        }
        return new Label[]{heading,caption,clock};
    }
    private static Bitmap mask(String text,float size,int maxWidth,int maxLines){
        TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setTextSize(size);paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        int padding=Math.max(1,Math.round(size*.12f));
        StaticLayout layout=StaticLayout.Builder.obtain(text,0,text.length(),paint,Math.max(1,maxWidth-padding*2))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(size*.12f,1)
            .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build();
        float width=1;for(int i=0;i<layout.getLineCount();i++)width=Math.max(width,layout.getLineWidth(i));
        Bitmap bitmap=Bitmap.createBitmap(Math.min(maxWidth,(int)Math.ceil(width)+padding*2),Math.max(1,layout.getHeight()+padding*2),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.translate(padding,padding);layout.draw(canvas);return bitmap;
    }
    private static void recycle(Label[] labels){if(labels!=null)for(Label label:labels)if(label!=null)label.mask().recycle();}

    private static final class Program extends BaseGlShaderProgram {
        private final GlProgram program;
        private final Clip clip;
        private final String day;
        private final boolean showTime;
        private final int[] textures={-1,-1,-1};
        Program(Context context,boolean hdr,Clip clip,String day,boolean showTime) throws VideoFrameProcessingException{
            super(hdr,1);this.clip=clip;this.day=day;this.showTime=showTime;
            try{
                program=new GlProgram(context,"shaders/vertex_shader_transformation_es2.glsl","shaders/captions.glsl");
                program.setBufferAttribute("aFramePosition",GlUtil.getNormalizedCoordinateBounds(),GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
                program.setFloatsUniform("uTransformationMatrix",GlUtil.create4x4IdentityMatrix());
                program.setFloatsUniform("uTexTransformationMatrix",GlUtil.create4x4IdentityMatrix());
                program.setFloatsUniform("uLumaWeights",hdr?new float[]{.2627f,.6780f,.0593f}:new float[]{.2126f,.7152f,.0722f});
                program.setFloatUniform("uLumaThreshold",CaptionColors.CROSSOVER);
            }catch(IOException|GlUtil.GlException e){throw new VideoFrameProcessingException(e);}
        }
        @Override public Size configure(int width,int height) throws VideoFrameProcessingException{
            Label[] labels=labels(width,height,clip,day,showTime);
            String[] names={"Day","Clip","Time"};
            try{
                for(int i=0;i<3;i++){
                    if(textures[i]!=-1)GlUtil.deleteTexture(textures[i]);
                    Label label=labels[i];Bitmap bitmap=label==null?Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888):label.mask();
                    textures[i]=GlUtil.createTexture(bitmap.getWidth(),bitmap.getHeight(),false);GlUtil.setTexture(textures[i],bitmap);
                    program.setSamplerTexIdUniform("u"+names[i]+"Mask",textures[i],i+1);
                    float[] rect=label==null?new float[4]:new float[]{label.x()/width,(height-label.y()-bitmap.getHeight())/height,bitmap.getWidth()/(float)width,bitmap.getHeight()/(float)height};
                    program.setFloatsUniform("u"+names[i]+"Rect",rect);
                    if(label==null)bitmap.recycle();
                }
                return new Size(width,height);
            }catch(GlUtil.GlException e){throw new VideoFrameProcessingException(e);}
            finally{recycle(labels);}
        }
        @Override public void drawFrame(int inputTexId,long timeUs) throws VideoFrameProcessingException{
            try{
                program.use();program.setSamplerTexIdUniform("uTexSampler",inputTexId,0);program.bindAttributesAndUniforms();
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GlUtil.checkGlError();
            }catch(GlUtil.GlException e){throw new VideoFrameProcessingException(e,timeUs);}
        }
        @Override public void release() throws VideoFrameProcessingException{
            super.release();try{for(int texture:textures)if(texture!=-1)GlUtil.deleteTexture(texture);program.delete();}
            catch(GlUtil.GlException e){throw new VideoFrameProcessingException(e);}
        }
    }

    static final class Preview extends View {
        private Clip clip;
        private String day;
        private boolean showTime,pending;
        private Label[] labels;
        private Bitmap background;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler main=new Handler(Looper.getMainLooper());
        private Runnable probe;
        Preview(Context context,Clip clip,String day,boolean showTime){
            super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);update(clip,day,showTime);
        }
        void update(Clip clip,String day,boolean showTime){this.clip=clip;this.day=day;this.showTime=showTime;clearLabels();invalidate();}
        void startProbing(VideoView video,File source,ExecutorService io){
            probe=new Runnable(){@Override public void run(){
                if(!isAttachedToWindow()||io.isShutdown())return;
                if(!pending&&(showTime||!day.isBlank()||!clip.caption().isBlank())){
                    pending=true;long position=video.getCurrentPosition()*1000L;
                    io.execute(()->{
                        Bitmap frame=null;
                        try(MediaMetadataRetriever media=new MediaMetadataRetriever()){
                            media.setDataSource(source.getAbsolutePath());frame=media.getScaledFrameAtTime(position,MediaMetadataRetriever.OPTION_CLOSEST,clip.landscape()?320:180,clip.landscape()?180:320);
                        }catch(Exception e){android.util.Log.w("DaylogCaptions","Preview frame unavailable",e);}
                        Bitmap result=frame;main.post(()->{pending=false;if(!isAttachedToWindow()){if(result!=null)result.recycle();return;}if(background!=null)background.recycle();background=result;invalidate();});
                    });
                }
                main.postDelayed(this,500);
            }};main.post(probe);
        }
        private void clearLabels(){recycle(labels);labels=null;}
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){clearLabels();}
        @Override protected void onDetachedFromWindow(){clearLabels();if(probe!=null)main.removeCallbacks(probe);if(background!=null){background.recycle();background=null;}super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas canvas){
            if(getWidth()==0||getHeight()==0)return;
            if(labels==null)labels=labels(getWidth(),getHeight(),clip,day,showTime);
            for(Label label:labels)if(label!=null){
                double luminance=0;
                if(background!=null){
                    for(int y=0;y<3;y++)for(int x=0;x<5;x++){
                        int px=Math.max(0,Math.min(background.getWidth()-1,(int)((label.x()+label.mask().getWidth()*(x+.5f)/5)*background.getWidth()/getWidth())));
                        int py=Math.max(0,Math.min(background.getHeight()-1,(int)((label.y()+label.mask().getHeight()*(y+.5f)/3)*background.getHeight()/getHeight())));
                        luminance+=CaptionColors.luminance(background.getPixel(px,py));
                    }
                    luminance/=15;
                }
                paint.setColorFilter(new PorterDuffColorFilter(CaptionColors.darkInk(luminance)?Color.BLACK:Color.WHITE,PorterDuff.Mode.SRC_IN));
                canvas.drawBitmap(label.mask(),label.x(),label.y(),paint);
            }
        }
    }
}
