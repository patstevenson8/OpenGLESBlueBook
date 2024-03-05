package edu.gatech.gtri.geomipmap;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;

public class GeoMipMap extends AppCompatActivity
{
    @Override
    protected void onCreate ( Bundle savedInstanceState )
    {
        super.onCreate ( savedInstanceState );

        // Check for support of OpenGL ES 3.0
        // If not supported, exit
        if ( detectOpenGLES30() )
        {
            // Set the main content with the full set of resources
            setContentView(R.layout.activity_main);

            // The LinearLayout is used to hold the GL view
            glView = (ConstraintLayout)findViewById(R.id.glView);

            // Create a GLSurfaceView instance
            mGLSurfaceView = new GeoMipMapView(this);

            // Now add the GLSurfaceView to the layout component
            glView.addView(mGLSurfaceView, 0);
        }
        else
        {
            Log.e ( "GeoMipMap", "OpenGL ES 3.0 not supported on device.  Exiting..." );
            finish();
        }
    }

    private boolean detectOpenGLES30()
    {
        ActivityManager am =
                ( ActivityManager ) getSystemService ( Context.ACTIVITY_SERVICE );
        ConfigurationInfo info = am.getDeviceConfigurationInfo();
        return ( info.reqGlEsVersion >= 0x30000 );
    }

    @Override
    protected void onResume()
    {
        // Ideally a game should implement onResume() and onPause()
        // to take appropriate action when the activity looses focus
        super.onResume();
        mGLSurfaceView.onResume();
    }

    @Override
    protected void onPause()
    {
        // Ideally a game should implement onResume() and onPause()
        // to take appropriate action when the activity looses focus
        super.onPause();
        mGLSurfaceView.onPause();
    }

    private ConstraintLayout glView;
    private GeoMipMapView mGLSurfaceView;
}