package edu.gatech.gtri.terrainrendering;

import static javax.microedition.khronos.opengles.GL11.GL_ELEMENT_ARRAY_BUFFER;
import static javax.microedition.khronos.opengles.GL11.GL_STATIC_DRAW;
import static javax.microedition.khronos.opengles.GL11.GL_ARRAY_BUFFER;

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
import java.nio.IntBuffer;

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
   private void loadTextureFromAsset ( String fileName )
   {
      textureId[0] = 0;
      Bitmap bitmap;
      InputStream is;

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
         return;
      }

      bitmap = BitmapFactory.decodeStream ( is );

      IntBuffer textureIBO = ByteBuffer.allocateDirect ( 4 ).order ( ByteOrder.nativeOrder() ).asIntBuffer();
      GLES30.glGenTextures ( 1, textureIBO );
      GLES30.glBindTexture ( GLES30.GL_TEXTURE_2D, textureId[0] );

      GLUtils.texImage2D ( GLES30.GL_TEXTURE_2D, 0, bitmap, 0 );

      GLES30.glTexParameteri ( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR );
      GLES30.glTexParameteri ( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR );
      GLES30.glTexParameteri ( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE );
      GLES30.glTexParameteri ( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE );
   }

   ///
   // Initialize the shader and program object
   //
   public void onSurfaceCreated ( GL10 glUnused, EGLConfig config )
   {
      String vShaderStr =
         "#version 300 es                                                                \n" +
            "uniform mat4 u_mvpMatrix;                                                   \n" +
            "uniform vec3 u_lightDirection;                                              \n" +
            "layout(location = 0) in vec4 a_position;                                    \n" +
            "uniform sampler2D s_texture;                                                \n" +
            "out vec4 v_color;                                                           \n" +
            "void main()                                                                 \n" +
            "{                                                                           \n" +
            "   // compute vertex normal from height map                                 \n" +
            "   float hxl = textureOffset( s_texture, a_position.xy, ivec2(-1,  0) ).r;  \n" +
            "   float hxr = textureOffset( s_texture, a_position.xy, ivec2( 1,  0) ).r;  \n" +
            "   float hyl = textureOffset( s_texture, a_position.xy, ivec2( 0, -1) ).r;  \n" +
            "   float hyr = textureOffset( s_texture, a_position.xy, ivec2( 0,  1) ).r;  \n" +
            "   vec3 u = normalize( vec3(0.05, 0.0, hxr-hxl) );                          \n" +
            "   vec3 v = normalize( vec3(0.0, 0.05, hyr-hyl) );                          \n" +
            "   vec3 normal = cross( u, v );                                             \n" +
            "                                                                            \n" +
            "   // compute diffuse lighting                                              \n" +
            "   float diffuse = dot( normal, u_lightDirection );                         \n" +
            "   v_color = vec4( vec3(diffuse), 1.0 );                                    \n" +
            "                                                                            \n" +
            "   // get vertex position from height map                                   \n" +
            "   float h = texture ( s_texture, a_position.xy ).r;                        \n" +
            "   vec4 v_position = vec4 ( a_position.xy, h/2.5, a_position.w );           \n" +
            "   gl_Position = u_mvpMatrix * v_position;                                  \n" +
            "}                                                                           \n";

      String fShaderStr =
         "#version 300 es                                                                \n" +
            "precision mediump float;                                                    \n" +
            "in vec4 v_color;                                                            \n" +
            "layout(location = 0) out vec4 outColor;                                     \n" +
            "void main()                                                                 \n" +
            "{                                                                           \n" +
            "  outColor = v_color;                                                       \n" +
            "}                                                                           \n";

      // Load the shaders and get a linked program object
      mProgramObject = ESShader.loadProgram ( vShaderStr, fShaderStr );

      // Get the uniform locations
      mvpLoc = GLES30.glGetUniformLocation ( mProgramObject, "u_mvpMatrix" );
      lightDirectionLoc = GLES30.glGetUniformLocation ( mProgramObject, "u_lightDirection" );

      // Get the sampler location
      samplerLoc = GLES30.glGetUniformLocation ( mProgramObject, "s_texture" );

      // Load the heightmap texture images from 'assets'
      loadTextureFromAsset ( "textures/heightmap.png" );

      // Generate the position and indices of a square grid for the base terrain
      short gridSize = 200;
      numIndices = mSquareGrid.genSquareGrid (gridSize);

      // Initialize the VBO Ids
      mVBOIds[0] = 0;
      mVBOIds[1] = 1;

      // Index buffer for base terrain
      IntBuffer indicesIBO = ByteBuffer.allocateDirect ( 4 ).order ( ByteOrder.nativeOrder() ).asIntBuffer();
      GLES30.glGenBuffers ( 1, indicesIBO );
      GLES30.glBindBuffer ( GL_ELEMENT_ARRAY_BUFFER, mVBOIds[0] );
      GLES30.glBufferData ( GL_ELEMENT_ARRAY_BUFFER, numIndices * 2, mSquareGrid.getIndices(), GL_STATIC_DRAW );
      GLES30.glBindBuffer ( GL_ELEMENT_ARRAY_BUFFER, 0 );

      // Position VBO for base terrain
      IntBuffer positionVBO = ByteBuffer.allocateDirect ( 4 ).order ( ByteOrder.nativeOrder() ).asIntBuffer();
      GLES30.glGenBuffers ( 1, positionVBO );
      GLES30.glBindBuffer ( GL_ARRAY_BUFFER, mVBOIds[1] );
      GLES30.glBufferData ( GL_ARRAY_BUFFER, gridSize * gridSize * 4 * 3, mSquareGrid.getVertices(), GL_STATIC_DRAW );

      // Clear color
      GLES30.glClearColor( 1.0f, 1.0f, 1.0f, 0.0f );
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
      perspective.perspective ( 60.0f, aspect, 0.1f, 20.0f );

      // Generate a model view matrix to rotate/translate the terrain
      modelview.matrixLoadIdentity();

      // Center the terrain
      modelview.translate ( -0.5f, -0.5f, -0.7f );

      // Rotate
      modelview.rotate ( 45.0f, 1.0f, 0.0f, 0.0f );

      // Compute the final MVP by multiplying the
      // modelview and perspective matrices together
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
      GLES30.glBindBuffer ( GL_ARRAY_BUFFER, mVBOIds[1] );
      GLES30.glVertexAttribPointer ( POSITION_LOC, 3, GLES30.GL_FLOAT, false, 3 * 4, 0 );
      GLES30.glEnableVertexAttribArray ( POSITION_LOC );

      // Bind the index buffer
      GLES30.glBindBuffer ( GL_ELEMENT_ARRAY_BUFFER, mVBOIds[0] );

      // Bind the height map
      GLES30.glActiveTexture ( GLES30.GL_TEXTURE0 );
      GLES30.glBindTexture ( GLES30.GL_TEXTURE_2D, textureId[0] );

      // Set the vertex color to red
      GLES30.glVertexAttrib4f ( 1, 1.0f, 0.0f, 0.0f, 1.0f );

      // Load the MVP matrix
      GLES30.glUniformMatrix4fv ( mvpLoc, 1, false, mvpMatrix.getAsFloatBuffer() );

      // Load the light direction
      GLES30.glUniform3f ( lightDirectionLoc, 0.86f, 0.14f, 0.49f );

      // Set the height map sampler to texture unit to 0
      GLES30.glUniform1i ( samplerLoc, 0 );

      // Draw the grid
      GLES30.glDrawElements ( GLES30.GL_TRIANGLES, numIndices, GLES30.GL_UNSIGNED_SHORT, mSquareGrid.getIndices() );
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
   private final int [] textureId = new int[1];

   // VertexBufferObject Ids
   private final int [] mVBOIds = new int[2];

   // Number of indices
   private int numIndices;

   // Vertex data
   private final ESShapes mSquareGrid = new ESShapes();

   // MVP matrix
   private final ESTransform mvpMatrix = new ESTransform();

   // Additional member variables
   private int mWidth;
   private int mHeight;

   final int POSITION_LOC = 0;
   private final Context mContext;
}
