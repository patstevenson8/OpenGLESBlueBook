package edu.gatech.gtri.geomipmap;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;

import edu.gatech.gtri.common.ESShader;

public class GeoMMLandscape
{
    public GeoMMLandscape() throws IllegalArgumentException
    {
        // Setup the height map file
        setupFiles();

        // Read in the height map from disk
        try
        {
            // The map of altitude points (height map) that make up the terrain
            // The map width & height must be 2^n + 1
            heightMap = loadTerrain();
        }
        catch(Exception e)
        {
            e.printStackTrace();
            System.exit(0);
        }

        // Load the shaders and get a linked program object
        mProgramObject = ESShader.loadProgram ( vShaderStr, fShaderStr );

        //	Initialize the vertex array used for rendering triangle strips
        ByteBuffer vbb = ByteBuffer.allocateDirect(((kMapXSize+1)*2*3*4)); // (# of coordinate values * 4 bytes per float)
        vbb.order(ByteOrder.nativeOrder());
        vertexArray = vbb.asFloatBuffer();

        //	Initialize the color array
        ByteBuffer fbb = ByteBuffer.allocateDirect(((kMapXSize+1)*2*3*4)); // (# of coordinate values * 4 bytes per float)
        fbb.order(ByteOrder.nativeOrder());
        colorArray = fbb.asFloatBuffer();
    }

    // Set up the files the it needs
    private void setupFiles()
    {
        // Checks if external storage is available for read and write
        String state = Environment.getExternalStorageState();
        if(Environment.MEDIA_MOUNTED.equals(state)){
            Log.i(TAG, "External storage is good to go");}
        else{Log.e(TAG, "External storage not available");return;}

        // This is a hard coded filename to look for the height map
        // We have to check for its existence including its path
        File file = new File(kDataFilePath);
        Log.d(TAG, "File Exists: " + Boolean.toString(file.exists()));
        Log.d(TAG, "FilePath: " + file.getAbsolutePath());

        // We're checking for existence to a local directory on the device
        // and/or create it if necessary
        File dir = new File(Environment.getExternalStorageDirectory(),"GeoMipMap");
        Log.d(TAG, "External: " + Environment.getExternalStorageDirectory().toString());
        if(!dir.exists()){Log.d(TAG, "Directory Created: " + Boolean.toString(dir.mkdir()));}

        // This will check for the heightmap file if it exists
        // If not, exit
        //
        // In order to run this, the directory will have to be created first
        // and then the heightmap file will have to be manually copied onto
        // the device in the designated directory
        //
        // In the future, will try to get the app to install the directory
        // it needs and copy the heightmap file from the apk into the directory
        File fileInDir = new File(dir, file.getName());
        Log.d(TAG, "File Exists: " + Boolean.toString(fileInDir.exists()));
        Log.d(TAG, "FilePath: " + fileInDir.getAbsolutePath());
        File existingHeightFile = new File(dir, kDataFilePath);
        Log.d(TAG, "Existing File Exists: " + Boolean.toString(existingHeightFile.exists()));
        Log.d(TAG, "FilePath: " + existingHeightFile.getAbsolutePath());
        if(!existingHeightFile.exists()){System.exit(0);}
    }

    // This method loads the terrain data from a disk file
    private HeightMap loadTerrain() throws IOException
    {
        //	The file we want to read in.
        File filePath = new File(Environment.getExternalStorageDirectory() + "/GeoMipMap/", kDataFilePath);

        // Establish input stream for file
        FileInputStream inputStream = new FileInputStream(filePath);
        FileChannel channel = inputStream.getChannel();

        // Read the UHL and get some metadata
        channel.position(DTED_UHL_OFFSET);
        byte[] uhl = new byte[DTED_UHL_SIZE];
        ByteBuffer uhl_bb = ByteBuffer.wrap(uhl).order(ByteOrder.BIG_ENDIAN);
        channel.read(uhl_bb);
        uhl_bb.flip();

        // UHL read, now parse for the data

        // Longitude interval in 10ths of seconds, convert roughly to meters
        int width_spacing = Integer.valueOf(new String(uhl, 20, 4)) / 10 * 30;
        Log.d(TAG, "Longitude spacing  " + width_spacing);
        gGridSpacing = width_spacing;

        // Latitude interval in 10ths of seconds, convert roughly to meters
        int height_spacing = Integer.valueOf(new String(uhl, 24, 4)) / 10 * 30;
        Log.d(TAG, "Latitude spacing  "+ height_spacing);

        // Number of longitude lines
        kMapXSize = Integer.valueOf(new String(uhl, 47, 4));
        Log.d(TAG, "Number longitude lines  " + kMapXSize);

        // Number of latitude lines
        kMapYSize = Integer.valueOf(new String(uhl, 51, 4));
        Log.d(TAG, "Number of latitude lines  " + kMapYSize);

        // Pixel width
        double pixelWidth = 1d / ((double) (kMapXSize - 1));
        Log.d(TAG, "Width  " + pixelWidth);

        // Now prepare to read the elevation data
        channel.position(DTED_DATA_OFFSET);
        ByteBuffer bb = ByteBuffer.allocate(REC_HEADER_SIZE + kMapXSize * Short.SIZE / Byte.SIZE + REC_CHKSUM_SIZE);

        //	Map array has one additional row and column so that triangles at edges are more easily drawn
        int[][] height_data = new int[kMapYSize+1][kMapXSize+1];

        for(int x=0; x<kMapXSize; x++)
        {
            channel.read(bb);
            bb.flip();
            ShortBuffer data = bb.asShortBuffer();
            for(int i=0; i<kMapYSize; i++)
            {
                int elev = (int) data.get(i + 4);
                int y = kMapYSize - i - 1;
                height_data[x][y] = elev;
            }
            //	Copy last column into extra column
            height_data[x][kMapYSize] = height_data[x][kMapYSize-1];
        }
        //	Copy last row into extra row.
        System.arraycopy(height_data[kMapXSize-1], 0, height_data[kMapXSize], 0, kMapYSize);
        inputStream.close();
        return new HeightMap2Di(height_data, -Integer.MAX_VALUE);
    }

    // Render the landscape
    public void render(float[] mvpMatrix)
    {
        //	This block is visible, so render it
        //	Render the current mip-map level triangles in this block's mesh
        // Add program to OpenGL environment
        GLES30.glUseProgram(mProgramObject);

        // Get handle to vertex shader's vPosition member
        mPositionHandle = GLES30.glGetAttribLocation(mProgramObject, "vPosition");

        // Enable a handle to the triangle vertices
        GLES30.glEnableVertexAttribArray(mPositionHandle);

        // Prepare the triangle coordinate data
        GLES30.glVertexAttribPointer(mPositionHandle, COORDS_PER_VERTEX, GLES30.GL_FLOAT, false, 0, vertexArray);

        // Get handle to vertex shader's aColor member
        mColorHandle = GLES30.glGetAttribLocation(mProgramObject, "aColor");

        // Enable a handle to the triangle colors
        GLES30.glEnableVertexAttribArray(mColorHandle);

        // Prepare the triangle color data
        GLES30.glVertexAttribPointer(mColorHandle, COLORS_PER_VERTEX, GLES30.GL_FLOAT, false, 0, colorArray);

        // Get handle to transformation matrix
        mMVPMatrixHandle = GLES30.glGetUniformLocation(mProgramObject, "uMVPMatrix");

        // Apply the projection and view transformation
        GLES30.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0);

        // Scale the grid coordinates to model coordinates
        //Matrix.scaleM(mvpMatrix, 0, gGridSpacing, 0, gGridSpacing);

        //	Set the default color
        //gl.glColor3f(1,1,1);

        // All rendering is done with a triangle-strip vertex array (the verticies are filled
        // in and the rendering call made by each TerrainBlock object)
        //gl.glEnableClientState(GL.GL_VERTEX_ARRAY);
        //gl.glVertexPointer(3, GL.GL_FLOAT, 0, vertexArray);

        //	Enable a color array for the vertex colors
        //gl.glEnableClientState(GL.GL_COLOR_ARRAY);
        //gl.glColorPointer(3, GL.GL_FLOAT, 0, colorArray);

        //	Render
        int xStart = 0;
        int xEnd = xStart + kMapXSize;

        int yStart = 0;
        int yEnd = yStart + kMapYSize;

        //	Number of verticies in each triangle strip
        int vCount = ((xEnd - xStart)/step + 1)*2;

        //	Create a triangle strip for each row of the block
        for(int yPos=yStart; yPos<yEnd; yPos+=step)
        {
            //	Fill in vertex array with correct coordinates
            int idx = 0;
            int yPos2 = yPos + step;
            for(int xPos=xStart; xPos<=xEnd; xPos+=step)
            {
                idx = setVertexAndShading(idx, xPos, yPos);
            }

            // Vertices are set, indicate position
            vertexArray.position(0);
            colorArray.position(0);

            //	Render the triangles in the vertex array
            GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, 0, xEnd-xStart);
        }

        //	Create a triangle strip for each row of the block
        for(int xPos=xStart; xPos<yEnd; xPos+=step)
        {
            //	Fill in vertex array with correct coordinates
            int idx = 0;
            int xPos2 = xPos + step;
            for(int yPos=yStart; yPos<=yEnd; yPos+=step)
            {
                idx = setVertexAndShading(idx, xPos, yPos);
            }

            // Vertices are set, indicate position
            vertexArray.position(0);
            colorArray.position(0);

            //	Render the triangles in the vertex array
            GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, 0, yEnd-yStart);
        }

        // We are finished with color array
        GLES30.glDisableVertexAttribArray(mColorHandle);

        //	We are finished with vertex array
        GLES30.glDisableVertexAttribArray(mPositionHandle);
    }

    /**
     *  Set both the vertex in the vertex array and the shading in the color
     *  array.
     *
     *  @param  idx  Index to the next element in the vertex and color arrays.
     *  @param  xPos Horizontal position in the height map.
     *  @param  yPos Vertical position in the height map.
     *  @return The index to the next position in the vertex and color arrays.
     **/
    private int setVertexAndShading(int idx, int xPos, int yPos)
    {
        // Set the vertex
        float alt = heightMap.get(xPos, yPos);
        vertexArray.put(idx, xPos * gGridSpacing);
        vertexArray.put(idx + 1, alt);
        vertexArray.put(idx + 2, yPos * gGridSpacing);

        //	Set the shading
        float shade = 0.2f;
        colorArray.put(idx++, shade);
        colorArray.put(idx++, shade);
        colorArray.put(idx++, shade);

        return idx;
    }

    public float getSpacing(){return gGridSpacing;}
    public float getWidth(){return kMapXSize;}
    public float getHeight(){return kMapYSize;}
    public float get(int h, int v){return heightMap.get(h, v);}

    private final String vShaderStr =
            // This matrix member variable provides a hook to manipulate
            // the coordinates of the objects that use this vertex shader
            "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 vPosition;" +
                    "attribute vec4 aColor;" +
                    "varying vec4 vColor;" +
                    "void main()" +
                    "{" +
                    // Pass the color through to the fragment shader.
                    // It will be interpolated across the triangle.
                    "    vColor = aColor;" +

                    // The matrix must be included as a modifier of gl_Position.
                    // Note that the uMVPMatrix factor *must be first* in order
                    // for the matrix multiplication product to be correct.
                    "    gl_Position = uMVPMatrix * vPosition;" +
                    "}";

    private final String fShaderStr =
            "precision mediump float;" +
                    "varying vec4 vColor;" +
                    "void main()" +
                    "{" +
                    "  gl_FragColor = vColor;" +
                    "}";

    //	A vertex array to contain the coordinate data for rendering
    //	triangle strips in terrain blocks
    private FloatBuffer vertexArray;

    //	A color array containing colors assigned to each vertex in vertexArray
    private FloatBuffer colorArray;

    //private final ShortBuffer drawListBuffer;
    private int mProgramObject;
    private int mPositionHandle;
    private int mColorHandle;
    private int mMVPMatrixHandle;

    // Number of coordinates per vertex in this array
    static final int COORDS_PER_VERTEX = 3;

    // Number of colors per vertex in this array
    static final int COLORS_PER_VERTEX = 3;

    private final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    // Amount of space between map grid points in model coordinates
    private float gGridSpacing = 2;

    // Step size on base terrain map for current mip-map level
    private int step = 1;

    // Constants used for data file
    private int REC_HEADER_SIZE = 8;
    private int REC_CHKSUM_SIZE = Integer.SIZE / Byte.SIZE;
    private int DTED_UHL_SIZE = 80;
    private int DTED_DSI_SIZE = 648;
    private int DTED_ACC_SIZE = 2700;
    private long DTED_UHL_OFFSET = 0L;
    private long DTED_DSI_OFFSET = DTED_UHL_OFFSET + (long) DTED_UHL_SIZE;
    private long DTED_ACC_OFFSET = DTED_DSI_OFFSET + (long) DTED_DSI_SIZE;
    private long DTED_DATA_OFFSET = DTED_ACC_OFFSET + (long) DTED_ACC_SIZE;
    private int DTED_NODATA_VALUE = -32767;

    // Path to map data file
    private String kDataFilePath = "n33.dt0";

    //	The width and height of the altitude map
    private int kMapXSize = 0;
    private int kMapYSize = 0;

    //	The height map data
    private HeightMap heightMap;
}
