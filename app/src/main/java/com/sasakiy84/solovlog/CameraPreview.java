package com.sasakiy84.solovlog;

import android.content.Context;
import android.graphics.Color;
import android.view.TextureView;
import android.widget.FrameLayout;
import androidx.camera.view.PreviewView;

/** Both capture backends occupy the same stage and share the normal shooting controls. */
final class CameraPreview extends FrameLayout {
    final CameraProfiles.Route route;
    final boolean widestOnOpen;
    private PreviewView cameraX;
    private TextureView direct;
    CameraPreview(Context context,CameraProfiles.Route route){this(context,route,false);}
    CameraPreview(Context context,CameraProfiles.Route route,boolean widestOnOpen){super(context);this.route=route;this.widestOnOpen=widestOnOpen;setBackgroundColor(Color.BLACK);}
    PreviewView cameraX(){
        if(cameraX==null){removeAllViews();direct=null;cameraX=new PreviewView(getContext());addView(cameraX,new LayoutParams(-1,-1));}
        return cameraX;
    }
    TextureView direct(){
        if(direct==null){removeAllViews();cameraX=null;direct=new TextureView(getContext());addView(direct,new LayoutParams(-1,-1));}
        return direct;
    }
}
