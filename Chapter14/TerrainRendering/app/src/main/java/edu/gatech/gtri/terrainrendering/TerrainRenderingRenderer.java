package edu.gatech.gtri.terrainrendering;

import static javax.microedition.khronos.opengles.GL11.GL_ELEMENT_ARRAY_BUFFER;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import edu.gatech.gtri.common.ESShader;
import edu.gatech.gtri.common.ESShapes;
import edu.gatech.gtri.common.ESTransform;

public class TerrainRenderingRenderer implements GLSurfaceView.Renderer
{
   ///
   // Constructor
   //
   public TerrainRenderingRenderer ( Context context )
   {
      mContext = context;
   }

   ///
   //  Load texture from asset
   //
   private int loadTextureFromAsset ( String fileName )
   {
      int[] textureId = new int[1];
      Bitmap bitmap = null;
      InputStream is = null;

      try
      {
         is = mContext.getAssets().open ( fileName );
      }
      catch ( IOException ioe )
      {
         is = null;
      }

      if ( is == null )
      {
         return 0;
      }

      bitmap = BitmapFactory.decodeStream ( is );

      GLES30.glGenTextures ( 1, textureId, 0 );
      GLES30.glBindTexture ( GLES30.GL_TEXTURE_2D, textureId[0] );

      GLUtils.texImage2D ( GLES30.GL_TEXTURE_2D, 0, bitmap, 0 );

      GLES30.glTexParameteri ( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR );
      GLES30.glTexParameteri ( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR );
      GLES30.glTexParameteri ( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE );
      GLES30.glTexParameteri ( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE );

      return textureId[0];
   }

   ///
   // Initialize the shader and program object
   //
   public void onSurfaceCreated ( GL10 glUnused, EGLConfig config )
   {
      String vShaderStr =
         "#version 300 es                                      \n" +
            "uniform mat4 u_mvpMatrix;                            \n" +
            "uniform vec3 u_lightDirection;                       \n" +
            "layout(location = 0) in vec4 a_position;             \n" +
            "uniform sampler2D s_texture;                         \n" +
            "out vec4 v_color;                                    \n" +
            "void main()                                          \n" +
            "{                                                    \n" +
            "   // compute vertex normal from height map          \n" +
            "   float hxl = textureOffset( s_texture,             \n" +
            "                  a_position.xy, ivec2(-1,  0) ).w;  \n" +
            "   float hxr = textureOffset( s_texture,             \n" +
            "                  a_position.xy, ivec2( 1,  0) ).w;  \n" +
            "   float hyl = textureOffset( s_texture,             \n" +
            "                  a_position.xy, ivec2( 0, -1) ).w;  \n" +
            "   float hyr = textureOffset( s_texture,             \n" +
            "                  a_position.xy, ivec2( 0,  1) ).w;  \n" +
            "   vec3 u = normalize( vec3(0.05, 0.0, hxr-hxl) );   \n" +
            "   vec3 v = normalize( vec3(0.0, 0.05, hyr-hyl) );   \n" +
            "   vec3 normal = cross( u, v );                      \n" +
            "                                                     \n" +
            "   // compute diffuse lighting                       \n" +
            "   float diffuse = dot( normal, u_lightDirection );  \n" +
            "   v_color = vec4( vec3(diffuse), 1.0 );             \n" +
            "                                                     \n" +
            "   // get vertex position from height map            \n" +
            "   float h = texture ( s_texture, a_position.xy ).w; \n" +
            "   vec4 v_position = vec4 ( a_position.xy,           \n" +
            "                            h/2.5,                   \n" +
            "                            a_position.w );          \n" +
            "   gl_Position = u_mvpMatrix * v_position;           \n" +
            "}                                                    \n";

      String fShaderStr =
         "#version 300 es                                      \n" +
            "precision mediump float;                             \n" +
            "in vec4 v_color;                                     \n" +
            "layout(location = 0) out vec4 outColor;              \n" +
            "void main()                                          \n" +
            "{                                                    \n" +
            "  outColor = v_color;                                \n" +
            "}                                                    \n";

      // Load the shaders and get a linked program object
      mProgramObject = ESShader.loadProgram ( vShaderStr, fShaderStr );

      // Get the uniform locations
      mvpLoc = GLES30.glGetUniformLocation ( mProgramObject, "u_mvpMatrix" );
      lightDirectionLoc = GLES30.glGetUniformLocation ( mProgramObject, "u_lightDirection" );

      // Get the sampler location
      samplerLoc = GLES30.glGetUniformLocation ( mProgramObject, "s_texture" );

      GLES30.glClearColor ( 1.0f, 1.0f, 1.0f, 0.0f );

      // Load the heightmap texture images from 'assets'
      textureId = loadTextureFromAsset ( "textures/heightmap.tga" );

      // Generate the position and indices of a square grid for the base terrain
      gridSize = 200;
      mSquareGrid.genSquareGrid (gridSize);

      // Index buffer for base terrain
      GLES30.glGenBuffers ( 1, mSquareGrid.getIndices() );
      GLES30.glBindBuffer ( GL_ELEMENT_ARRAY_BUFFER, indicesIBO );
      GLES30.glBufferData ( GL_ELEMENT_ARRAY_BUFFER, mSquareGrid.getNumIndices() * sizeof ( GLuint ), indices, GL_STATIC_DRAW );
      GLES30.glBindBuffer ( GL_ELEMENT_ARRAY_BUFFER, 0 );
   }

   private void update()
   {
      ESTransform perspective = new ESTransform();
      ESTransform modelview = new ESTransform();
      float aspect;

      // Compute the window aspect ratio
      aspect = ( float ) mWidth / ( float ) mHeight;

      // Generate a perspective matrix with a 60 degree FOV
      perspective.matrixLoadIdentity();
      perspective.perspective ( 60.0f, aspect, 1.0f, 20.0f );

      // Generate a model view matrix to rotate/translate the terrain
      modelview.matrixLoadIdentity();

      // Center the terrain
      modelview.translate ( -0.5f, -0.5f, -0.7f );

      // Rotate
      modelview.rotate ( 45.0f, 1.0f, 0.0f, 0.0f );

      // Compute the final MVP by multiplying the
      // modevleiw and perspective matrices together
      mvpMatrix.matrixMultiply ( modelview.get(), perspective.get() );
   }

   ///
   // Draw a flat grid
   //
   public void onDrawFrame ( GL10 glUnused )
   {
      update();

      // Set the view-port
      GLES30.glViewport ( 0, 0, mWidth, mHeight );

      // Clear the color buffer
      GLES30.glClear ( GLES30.GL_COLOR_BUFFER_BIT );

      // Use the program object
      GLES30.glUseProgram ( mProgramObject );

      // Load the vertex position
      mSquareGrid.getVertices().position ( 0 );
      GLES30.glVertexAttribPointer ( 0, 3, GLES30.GL_FLOAT, false, 5 * 4, mSquareGrid.getVertices() );

      // Load the texture coordinate
      mSquareGrid.getVertices().position ( 3 );
      GLES30.glVertexAttribPointer ( 1, 2, GLES30.GL_FLOAT, false, 5 * 4, mSquareGrid.getVertices() );

      GLES30.glEnableVertexAttribArray ( 0 );
      GLES30.glEnableVertexAttribArray ( 1 );

      // Bind the base map
      GLES30.glActiveTexture ( GLES30.GL_TEXTURE0 );
      GLES30.glBindTexture ( GLES30.GL_TEXTURE_2D, mBaseMapTexId );

      // Set the base map sampler to texture unit to 0
      GLES30.glUniform1i ( mBaseMapLoc, 0 );

      // Bind the light map
      GLES30.glActiveTexture ( GLES30.GL_TEXTURE1 );
      GLES30.glBindTexture ( GLES30.GL_TEXTURE_2D, mLightMapTexId );

      // Set the light map sampler to texture unit 1
      GLES30.glUniform1i ( mLightMapLoc, 1 );

      GLES30.glDrawElements ( GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_SHORT, mIndices );
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
   private int lightDirectionLoc;

   // Sampler location
   private int samplerLoc;

   // Texture handle
   private int textureId;

   // VBOs
   private int positionVBO;
   private int indicesIBO;

   // Number of indices
   private int numIndices;

   // Dimension of grid
   private int gridSize;

   // Vertex data
   private ESShapes mSquareGrid = new ESShapes();

   // MVP matrix
   private ESTransform mvpMatrix = new ESTransform();

   // Additional member variables
   private int mWidth;
   private int mHeight;

   final int POSITION_LOC = 0;
   private Context mContext;
}
