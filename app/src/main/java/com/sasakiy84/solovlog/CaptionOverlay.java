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
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.StaticOverlaySettings;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.GlShaderProgram;
import java.io.File;
import java.util.concurrent.ExecutorService;

/** Preview and export share masks and one stable ink choice per label and clip. */
@UnstableApi
final class CaptionOverlay implements GlEffect {
    private final Clip clip;
    private final String dayCaption;
    private final boolean showTime,showDate;
    private final int[] preparedColors;
    private CaptionOverlay(Clip clip,String dayCaption,boolean showTime,boolean showDate,int[] colors){this.clip=clip;this.dayCaption=dayCaption;this.showTime=showTime;this.showDate=showDate;this.preparedColors=colors;}
    static CaptionOverlay prepare(Context context,File source,Clip clip,String dayCaption,boolean showTime,boolean showDate){
        Bitmap[] frames=sampleFrames(source,clip);
        try{return new CaptionOverlay(clip,dayCaption,showTime,showDate,clipColors(context,clip,dayCaption,showTime,showDate,frames));}
        finally{recycleFrames(frames);}
    }
    @Override public GlShaderProgram toGlShaderProgram(Context context,boolean useHdr) throws VideoFrameProcessingException{
        int[] colors=preparedColors;
        java.util.List<androidx.media3.effect.TextureOverlay> overlays=new java.util.ArrayList<>();
        for(int i=0;i<4;i++)overlays.add(new FixedLabel(context,clip,dayCaption,showTime,showDate,i,colors[i]));
        return new OverlayEffect(overlays).toGlShaderProgram(context,useHdr);
    }
    private record Label(Bitmap mask,float x,float y){}
    private static Label[] labels(Context context,int w,int h,Clip clip,String dayCaption,boolean showTime,boolean showDate){
        float edge=Math.min(w,h),marginX=w*.05f,marginY=h*.05f;
        Label clock=null,heading=null,caption=null,place=null;
        float available=w*.9f;
        if(showTime||showDate){
            String timestamp=(showDate?JournalRules.captureDate(clip):"")+(showDate&&showTime?"\n":"")+(showTime?JournalRules.captureTime(clip):"");
            Bitmap mask=mask(timestamp,edge*.051f,Math.round(available),2,Layout.Alignment.ALIGN_OPPOSITE);
            clock=new Label(mask,w-marginX-mask.getWidth(),h-marginY-mask.getHeight());
            available-=mask.getWidth()+edge*.04f;
        }
        if(!dayCaption.isBlank())heading=new Label(mask(dayCaption,edge*.057f,Math.round(w*.9f),3),marginX,marginY);
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
    private static Bitmap mask(String text,float size,int maxWidth,int maxLines){return mask(text,size,maxWidth,maxLines,Layout.Alignment.ALIGN_NORMAL);}
    private static Bitmap mask(String text,float size,int maxWidth,int maxLines,Layout.Alignment alignment){
        TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setTextSize(size);paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        int padding=Math.max(1,Math.round(size*.12f));
        int layoutWidth=Math.max(1,maxWidth-padding*2);
        if(alignment==Layout.Alignment.ALIGN_OPPOSITE){float measured=1;for(String line:text.split("\n"))measured=Math.max(measured,paint.measureText(line));layoutWidth=Math.min(layoutWidth,(int)Math.ceil(measured));}
        StaticLayout layout=StaticLayout.Builder.obtain(text,0,text.length(),paint,layoutWidth)
            .setAlignment(alignment).setIncludePad(false).setLineSpacing(size*.12f,1)
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
            for(int i=0;i<frames.length;i++)frames[i]=media.getScaledFrameAtTime(duration*(i*4+1)/10,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,clip.landscape()?320:180,clip.landscape()?180:320);
        }catch(Exception e){android.util.Log.w("DaylogCaptions","Clip color samples unavailable",e);}
        return frames;
    }
    private static void recycleFrames(Bitmap[] frames){if(frames!=null)for(Bitmap frame:frames)if(frame!=null)frame.recycle();}
    private static int[] labelColors(Label[] labels,Bitmap[] frames,int width,int height){
        int[] colors={Color.WHITE,Color.WHITE,Color.WHITE,Color.WHITE};
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
            if(samples==3&&CaptionColors.darkInk(darkest))colors[i]=Color.BLACK;
        }
        return colors;
    }
    private static int[] clipColors(Context context,Clip clip,String dayCaption,boolean showTime,boolean showDate,Bitmap[] frames){
        int width=clip.landscape()?1920:1080,height=clip.landscape()?1080:1920;
        Label[] reference=labels(context,width,height,clip,dayCaption,showTime,showDate);
        try{return labelColors(reference,frames,width,height);}finally{recycle(reference);}
    }
    private static void recycle(Label[] labels){if(labels!=null)for(Label label:labels)if(label!=null)label.mask().recycle();}

    private static final class FixedLabel extends BitmapOverlay {
        private final Context context;private final Clip clip;private final String dayCaption;private final boolean showTime,showDate;
        private final int index;private final int color;private Bitmap bitmap;
        private StaticOverlaySettings settings=new StaticOverlaySettings.Builder().build();
        FixedLabel(Context context,Clip clip,String dayCaption,boolean showTime,boolean showDate,int index,int color){this.context=context;this.clip=clip;this.dayCaption=dayCaption;this.showTime=showTime;this.showDate=showDate;this.index=index;this.color=color;}
        @Override public void configure(Size size){
            if(bitmap!=null)bitmap.recycle();
            Label[] labels=labels(context,size.getWidth(),size.getHeight(),clip,dayCaption,showTime,showDate);
            Label selected=labels[index];
            if(selected==null)bitmap=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);
            else{
                bitmap=selected.mask();new Canvas(bitmap).drawColor(color,PorterDuff.Mode.SRC_IN);
                settings=new StaticOverlaySettings.Builder().setOverlayFrameAnchor(-1,1)
                    .setBackgroundFrameAnchor(selected.x()/size.getWidth()*2-1,1-selected.y()/size.getHeight()*2).build();
            }
            for(int i=0;i<labels.length;i++)if(i!=index&&labels[i]!=null)labels[i].mask().recycle();
        }
        @Override public Bitmap getBitmap(long presentationTimeUs){return bitmap;}
        @Override public StaticOverlaySettings getOverlaySettings(long presentationTimeUs){return settings;}
        @Override public void release() throws VideoFrameProcessingException{super.release();if(bitmap!=null){bitmap.recycle();bitmap=null;}}
    }

    static final class Preview extends View {
        private Clip clip;
        private String dayCaption;
        private boolean showTime,showDate;
        private Label[] labels;
        private Bitmap[] backgrounds;
        private int[] colors;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int sourceGeneration;
        Preview(Context context,Clip clip,String dayCaption,boolean showTime,boolean showDate){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);update(clip,dayCaption,showTime,showDate);}
        void update(Clip clip,String dayCaption,boolean showTime,boolean showDate){
            if(this.clip!=null&&this.clip.equals(clip)&&this.dayCaption.equals(dayCaption)&&this.showTime==showTime&&this.showDate==showDate)return;
            this.clip=clip;this.dayCaption=dayCaption;this.showTime=showTime;this.showDate=showDate;clearLabels();invalidate();
        }
        void loadColors(File source,ExecutorService io){
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
            if(labels==null)labels=labels(getContext(),getWidth(),getHeight(),clip,dayCaption,showTime,showDate);
            if(colors==null)colors=clipColors(getContext(),clip,dayCaption,showTime,showDate,backgrounds);
            for(int i=0;i<labels.length;i++){Label label=labels[i];if(label!=null){
                paint.setColorFilter(new PorterDuffColorFilter(colors[i],PorterDuff.Mode.SRC_IN));
                canvas.drawBitmap(label.mask(),label.x(),label.y(),paint);
            }}
        }
    }
}
