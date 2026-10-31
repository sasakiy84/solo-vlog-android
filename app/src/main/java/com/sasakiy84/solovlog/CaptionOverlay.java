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

/** Preview and export share masks and one stable ink choice per label and clip. */
@UnstableApi
final class CaptionOverlay implements GlEffect {
    private final File source;
    private final Clip clip;
    private final String day;
    private final boolean showTime;
    CaptionOverlay(File source,Clip clip,String day,boolean showTime){this.source=source;this.clip=clip;this.day=day;this.showTime=showTime;}
    @Override public GlShaderProgram toGlShaderProgram(Context context,boolean useHdr) throws VideoFrameProcessingException{
        return new Program(context,useHdr,source,clip,day,showTime);
    }
    private record Label(Bitmap mask,float x,float y){}
    private static Label[] labels(Context context,int w,int h,Clip clip,String day,boolean showTime){
        float edge=Math.min(w,h),marginX=w*.05f,marginY=h*.05f;
        Label clock=null,heading=null,caption=null,place=null;
        float available=w*.9f;
        if(showTime){
            Bitmap mask=mask(JournalRules.captureTime(clip),edge*.051f,Math.round(available),1);
            clock=new Label(mask,w-marginX-mask.getWidth(),h-marginY-mask.getHeight());
            available-=mask.getWidth()+edge*.04f;
        }
        if(!day.isBlank())heading=new Label(mask(day,edge*.057f,Math.round(w*.9f),3),marginX,marginY);
        if(!clip.caption().isBlank()){
            Bitmap mask=mask(clip.caption(),edge*.06f,Math.max(1,Math.round(available)),6);
            caption=new Label(mask,marginX,h-marginY-mask.getHeight());
        }
        if(!clip.place().isBlank()){
            Bitmap mask=placeMask(context,clip.place(),edge*.048f,Math.max(1,Math.round(available)));
            place=new Label(mask,marginX,h-marginY-mask.getHeight()-(caption==null?0:caption.mask().getHeight()+edge*.012f));
        }
        return new Label[]{heading,caption,place,clock};
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
    private static Bitmap placeMask(Context context,String text,float size,int maxWidth){
        int iconSize=Math.max(1,Math.round(size*1.1f)),gap=Math.max(1,Math.round(size*.25f));
        Bitmap words=mask(text,size,Math.max(1,maxWidth-iconSize-gap),2);
        Bitmap bitmap=Bitmap.createBitmap(Math.min(maxWidth,words.getWidth()+iconSize+gap),Math.max(words.getHeight(),iconSize),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);float top=(bitmap.getHeight()-iconSize)/2f;
        android.graphics.drawable.Drawable pin=Ui.drawable(context,"pin",Color.WHITE);
        pin.setBounds(0,Math.round(top),iconSize,Math.round(top)+iconSize);pin.draw(canvas);
        canvas.drawBitmap(words,iconSize+gap,(bitmap.getHeight()-words.getHeight())/2f,null);words.recycle();return bitmap;
    }
    private static Bitmap[] sampleFrames(File source,Clip clip){
        Bitmap[] frames=new Bitmap[3];
        try(MediaMetadataRetriever media=new MediaMetadataRetriever()){
            media.setDataSource(source.getAbsolutePath());
            long duration=JournalRules.exportDuration(clip)*1000L;
            for(int i=0;i<frames.length;i++)frames[i]=media.getScaledFrameAtTime(duration*(i*4+1)/10,MediaMetadataRetriever.OPTION_CLOSEST,clip.landscape()?320:180,clip.landscape()?180:320);
        }catch(Exception e){android.util.Log.w("DaylogCaptions","Clip color samples unavailable",e);}
        return frames;
    }
    private static void recycleFrames(Bitmap[] frames){if(frames!=null)for(Bitmap frame:frames)if(frame!=null)frame.recycle();}
    private static float[] inks(Label[] labels,Bitmap[] frames,int width,int height){
        float[] colors={1,1,1,1};
        if(frames==null)return colors;
        for(int i=0;i<labels.length;i++){
            Label label=labels[i];if(label==null)continue;
            double darkest=1;int samples=0;
            for(Bitmap frame:frames)if(frame!=null){
                double luminance=0;
                for(int y=0;y<3;y++)for(int x=0;x<5;x++){
                    int px=Math.max(0,Math.min(frame.getWidth()-1,(int)((label.x()+label.mask().getWidth()*(x+.5f)/5)*frame.getWidth()/width)));
                    int py=Math.max(0,Math.min(frame.getHeight()-1,(int)((label.y()+label.mask().getHeight()*(y+.5f)/3)*frame.getHeight()/height)));
                    luminance+=CaptionColors.luminance(frame.getPixel(px,py));
                }
                darkest=Math.min(darkest,luminance/15);samples++;
            }
            if(samples==3&&CaptionColors.darkInk(darkest))colors[i]=0;
        }
        return colors;
    }
    private static float[] clipInks(Context context,Clip clip,String day,boolean showTime,Bitmap[] frames){
        int width=clip.landscape()?1920:1080,height=clip.landscape()?1080:1920;
        Label[] reference=labels(context,width,height,clip,day,showTime);
        try{return inks(reference,frames,width,height);}finally{recycle(reference);}
    }
    private static void recycle(Label[] labels){if(labels!=null)for(Label label:labels)if(label!=null)label.mask().recycle();}

    private static final class Program extends BaseGlShaderProgram {
        private final GlProgram program;
        private final Context context;
        private final File source;
        private final Clip clip;
        private final String day;
        private final boolean showTime;
        private final int[] textures={-1,-1,-1,-1};
        Program(Context context,boolean hdr,File source,Clip clip,String day,boolean showTime) throws VideoFrameProcessingException{
            super(hdr,1);this.context=context;this.source=source;this.clip=clip;this.day=day;this.showTime=showTime;
            try{
                program=new GlProgram(context,"shaders/vertex_shader_transformation_es2.glsl","shaders/captions.glsl");
                program.setBufferAttribute("aFramePosition",GlUtil.getNormalizedCoordinateBounds(),GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
                program.setFloatsUniform("uTransformationMatrix",GlUtil.create4x4IdentityMatrix());
                program.setFloatsUniform("uTexTransformationMatrix",GlUtil.create4x4IdentityMatrix());
            }catch(IOException|GlUtil.GlException e){throw new VideoFrameProcessingException(e);}
        }
        @Override public Size configure(int width,int height) throws VideoFrameProcessingException{
            Label[] labels=labels(context,width,height,clip,day,showTime);
            String[] names={"Day","Clip","Place","Time"};
            Bitmap[] frames=sampleFrames(source,clip);
            program.setFloatsUniform("uInkColors",clipInks(context,clip,day,showTime,frames));
            recycleFrames(frames);
            try{
                for(int i=0;i<4;i++){
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
        private boolean showTime;
        private Label[] labels;
        private Bitmap[] backgrounds;
        private float[] colors;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int sourceGeneration;
        Preview(Context context,Clip clip,String day,boolean showTime){
            super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);update(clip,day,showTime);
        }
        void update(Clip clip,String day,boolean showTime){
            if(this.clip!=null&&this.clip.equals(clip)&&this.day.equals(day)&&this.showTime==showTime)return;
            this.clip=clip;this.day=day;this.showTime=showTime;clearLabels();invalidate();
        }
        void startProbing(VideoView video,File source,ExecutorService io){
            int generation=++sourceGeneration;Clip sampledClip=clip;setAlpha(0f);
            io.execute(()->{
                Bitmap[] frames=sampleFrames(source,sampledClip);
                post(()->{if(generation!=sourceGeneration){recycleFrames(frames);return;}recycleFrames(backgrounds);backgrounds=frames;colors=null;setAlpha(1f);invalidate();});
            });
        }
        private void clearLabels(){recycle(labels);labels=null;colors=null;}
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){clearLabels();}
        @Override protected void onDetachedFromWindow(){sourceGeneration++;clearLabels();recycleFrames(backgrounds);backgrounds=null;super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas canvas){
            if(getWidth()==0||getHeight()==0)return;
            if(labels==null)labels=labels(getContext(),getWidth(),getHeight(),clip,day,showTime);
            if(colors==null)colors=clipInks(getContext(),clip,day,showTime,backgrounds);
            for(int i=0;i<labels.length;i++){Label label=labels[i];if(label!=null){
                paint.setColorFilter(new PorterDuffColorFilter(colors[i]==0?Color.BLACK:Color.WHITE,PorterDuff.Mode.SRC_IN));
                canvas.drawBitmap(label.mask(),label.x(),label.y(),paint);
            }}
        }
    }
}
