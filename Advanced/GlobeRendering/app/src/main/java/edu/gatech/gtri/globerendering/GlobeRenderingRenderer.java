package edu.gatech.gtri.globerendering;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import edu.gatech.gtri.common.ESShader;
import edu.gatech.gtri.common.ESShapes;
import edu.gatech.gtri.common.ESTransform;

public class GlobeRenderingRenderer implements GLSurfaceView.Renderer
{
   ///
   // Constructor
   //
   public GlobeRenderingRenderer ( Context context )
   {
      mContext = context;
   }

   ///
   //  Load texture from asset
   //
   private void loadTextureFromAsset ( )
   {
      textureId[0] = 0;
      Bitmap bitmap;
      InputStream is;

      try
      {
         is = mContext.getAssets().open ("textures/worldtopo1024x1024.png");
      }
      catch ( IOException ioe )
      {
         is = null;
      }

      if ( is == null )
      {
         return;
      }

      bitmap = BitmapFactory.decodeStream ( is );

      GLES30.glGenTextures ( 1, textureId, 0 );
      GLES30.glBindTexture ( GLES30.GL_TEXTURE_2D, textureId[0] );

      GLUtils.texImage2D ( GLES30.GL_TEXTURE_2D, 0, bitmap, 0 );

      GLES30.glTexParameteri ( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR );
      GLES30.glTexParameteri ( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR );
   }

   ///
   // Initialize the shader and program object
   //
   public void onSurfaceCreated (GL10 glUnused, EGLConfig config )
   {
      // Load shaders from 'assets' and get a linked program object
      mProgramObject = ESShader.loadProgramFromAsset ( mContext,
         "shaders/vertexShader.vert",
         "shaders/fragmentShader.frag");

      // Get the uniform locations
      mvpLoc = GLES30.glGetUniformLocation ( mProgramObject, "u_mvpMatrix" );

      // Get the sampler location
      samplerLoc = GLES30.glGetUniformLocation ( mProgramObject, "s_texture" );

      // Load the heightmap texture images from 'assets'
      loadTextureFromAsset ();

      // Generate the position and indices of a globe
      // Earth radius 6378 km
      mGlobe.genSphere ( 250, 6378.0f );

      // Clear color
      GLES30.glClearColor( 1.0f, 1.0f, 1.0f, 0.0f );
   }

   private void update()
   {
      if ( mLastTime == 0 )
      {
         mLastTime = SystemClock.uptimeMillis();
      }

      long curTime = SystemClock.uptimeMillis();
      long elapsedTime = curTime - mLastTime;
      float deltaTime = elapsedTime / 2000.0f;
      mLastTime = curTime;

      ESTransform perspective = new ESTransform();
      ESTransform modelview = new ESTransform();
      float aspect;

      // Compute a rotation angle based on time to rotate the cube
      mAngle += ( deltaTime * 40.0f );

      if ( mAngle >= 360.0f )
      {
         mAngle -= 360.0f;
      }

      // Compute the window aspect ratio
      aspect = ( float ) mWidth / ( float ) mHeight;

      // Generate a perspective matrix with a 60 degree FOV
      perspective.matrixLoadIdentity();
      perspective.perspective ( 60.0f, aspect, 1.0f, 12756.0f );

      // Generate a model view matrix to rotate/translate the cube
      modelview.matrixLoadIdentity();

      // Translate away from the viewer
      modelview.translate ( 0.0f, 0.0f, -12756.0f );

      // Rotate the cube
      modelview.rotate ( mAngle, 0.0f, -1.0f, 0.0f );

      // Compute the final MVP by multiplying the
      // modevleiw and perspective matrices together
      mvpMatrix.matrixMultiply ( modelview.get(), perspective.get() );
   }

   ///
   // Draw the globe
   //
   public void onDrawFrame ( GL10 glUnused )
   {
      update();

      // Set the view-port
      GLES30.glViewport ( 0, 0, mWidth, mHeight );

      // Clear the color buffer
      GLES30.glClear ( GLES30.GL_COLOR_BUFFER_BIT );

      GLES30.glCullFace ( GLES30.GL_FRONT );
      GLES30.glEnable ( GLES30.GL_CULL_FACE );

      // Use the program object
      GLES30.glUseProgram ( mProgramObject );

      // Load the vertex position
      GLES30.glVertexAttribPointer ( 0, 3, GLES30.GL_FLOAT, false, 0, mGlobe.getVertices() );
      GLES30.glEnableVertexAttribArray ( 0 );

      // Load the texture coordinate
      GLES30.glVertexAttribPointer ( 1, 2, GLES30.GL_FLOAT, false, 0, mGlobe.getTexCoords() );
      GLES30.glEnableVertexAttribArray ( 1 );

      // Bind the texture
      GLES30.glActiveTexture ( GLES30.GL_TEXTURE0 );
      GLES30.glBindTexture ( GLES30.GL_TEXTURE_2D, textureId[0] );

      // Load the MVP matrix
      GLES30.glUniformMatrix4fv ( mvpLoc, 1, false, mvpMatrix.getAsFloatBuffer() );

      // Set the texture sampler to texture unit to 0
      GLES30.glUniform1i ( samplerLoc, 0 );

      // Draw the globe
      GLES30.glDrawElements ( GLES30.GL_TRIANGLES, mGlobe.getNumIndices(), GLES30.GL_UNSIGNED_SHORT, mGlobe.getIndices() );
   }

   ///
   // Handle surface changes
   //
   public void onSurfaceChanged (GL10 glUnused, int width, int height )
   {
      mWidth = width;
      mHeight = height;
   }

   // Handle to a program object
   private int mProgramObject;

   // Uniform locations
   private int mvpLoc;

   // Sampler location
   private int samplerLoc;

   // Texture handle
   private final int [] textureId = new int[1];

   // Vertex data
   private final ESShapes mGlobe = new ESShapes();

   // Rotation angle
   private float mAngle;

   // MVP matrix
   private final ESTransform mvpMatrix = new ESTransform();

   // Additional member variables
   private int mWidth;
   private int mHeight;

   private final Context mContext;

   // Additional member variables
   private long mLastTime = 0;
}
