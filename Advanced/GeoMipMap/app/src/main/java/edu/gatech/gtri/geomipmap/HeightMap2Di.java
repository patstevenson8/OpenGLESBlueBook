package edu.gatech.gtri.geomipmap;

//  A height map backed by a 2D array of integer altitude values.
//  This implementation allows you to specify a "coded undefined"
//  altitude value.  A magic altitude is defined such that any
//  altitude value less than that is considered "undefined".  However,
//  those undefined values are coded with "extrapolated" or filled
//  values using the formula:  code = 2*magic# - altitude.  The magic
//  number is generally a negative altitude such as -32768 or -99999.
public class HeightMap2Di implements HeightMap
{
    /**
     *  Constructs a new height map based on a 2D array of integer altitude
     *  points.  The vertical direction is considered to be the 1st dimension
     *  and the horizontal direction is assumed to be the 2nd dimension:
     *  e.g.:  get(h,v) returns hm[v][h].
     *
     *  @param  heightMap  The 2D array of integers containing the altitude data.
     *  @param  codedUndef A code indicating integer values less than this are
     *                     undefined, but coded with a fill value.  To not
     *                     use this feature, pass (-Integer.MAX_VALUE).
     **/
    public HeightMap2Di(int[][] heightMap, int codedUndef)
    {
        hm = heightMap;
        undefined = codedUndef;
        findMinMaxAlt();
    }

    /**
     *  Constructs a new height map based on a 2D array of integer altitude
     *  points.  The vertical direction is considered to be the 1st dimension
     *  and the horizontal direction is assumed to be the 2nd dimension:
     *  e.g.:  get(h,v) returns hm[v][h].
     *
     *  @param  heightMap  The 2D array of integers containing the altitude data.
     *  @param  codedUndef A code indicating integer values less than this are
     *                     undefined, but coded with a fill value.  To not
     *                     use this feature, pass (-Integer.MAX_VALUE).
     *  @param  minAlt     The minimum altitude in this height map.
     *  @param  maxAlt     The maximum altitude in this height map.
     **/
    public HeightMap2Di(int[][] heightMap, int codedUndef, int minAlt, int maxAlt)
    {
        hm = heightMap;
        undefined = codedUndef;
        this.minAlt = minAlt;
        this.maxAlt = maxAlt;
    }

    // Finds the minimum and maximum altitude in this height map
    private void findMinMaxAlt()
    {
        int width = getWidth();
        int height = getHeight();

        for(int i=0; i<height; ++i)
        {
            for(int j=0; j<width; ++j)
            {
                int alt = hm[i][j];
                if(alt > undefined)
                {
                    minAlt = Math.min(minAlt, alt);
                    maxAlt = Math.max(maxAlt, alt);
                }
            }
        }
    }

    /**
     *  Returns the altitude at the given horizontal and vertical position
     *  in the height map array.  The 1st coordinate (h) varies the most
     *  rapidly (best performance if "h" is the inner loop and "v" is the
     *  outer loop.
     *
     *  @param  h   The horizontal position the height array.
     *  @param  v   The vertical position in the height array.
     *  @return The altitude value at the specific location in the height map.
     **/
    public final float get(int h, int v)
    {
        int alt = hm[v][h];

        if(alt<=undefined)
        {
            alt = 2 * undefined - alt; // code = 2*undefined - altitude
        }

        return alt;
    }

    /**
     *  Returns the approximate normal vector at the specified horizontal and vertical
     *  position in the height map.  The normal is re-calculated every time this
     *  method is called.
     *
     *  @param  h   The horizontal position the height array.
     *  @param  v   The vertical position in the height array.
     *  @param  nv  An existing array that the normal vector will be placed into.
     *  @param  idx The index into the normal vector array where this normal
     *              will be placed.
     *  @return The index to the next position in the normal vector array after the
     *          one filled in by this method.
     **/
    public final int normal(int h, int v, float[] nv, int idx) {

        int width = getWidth() - 1;

        int dx = 2;
        int hm1 = h - 1;
        int hp1 = h + 1;
        if (hm1 < 0) {
            hm1 = 0;
            dx = 1;
        } else if (hp1 > width) {
            hp1 = width;
            dx = 1;
        }

        width = getHeight() - 1;
        int dz = 2;
        int vm1 = v - 1;
        int vp1 = v + 1;
        if (vm1 < 0) {
            vm1 = 0;
            dz = 1;
        } else if (vp1 > width) {
            vp1 = width;
            dz = width;
        }

        //	Calculate the normal vector.
        float nx = (get(hp1, v) - get(hm1, v))/dx;
        float nz = (get(h, vp1) - get(h, vm1))/dz;
        float ny = 1;

        //	Normalize the vector.
        float length = (float)Math.sqrt(nx*nx + 1 + nz*nz);
        if (length == 0)	length = 1;
        nx /= length;
        ny /= length;
        nz /= length;

        //	Fill in the output array.
        nv[idx++] = nx;
        nv[idx++] = ny;
        nv[idx++] = nz;

        return idx;
    }

    /**
     *  Returns the height of this altitude map.
     **/
    public final int getHeight() {
        return hm.length;
    }

    /**
     *  Returns the width of this altitude map.
     **/
    public final int getWidth() {
        return hm[0].length;
    }

    /**
     *  Returns the minimum altitude in the height map.
     *  Ignores any undefined points.
     **/
    public final float minAlt() {
        return minAlt;
    }

    /**
     *  Returns the maximum altitude in the height map.
     *  Ignores any undefined points.
     **/
    public final float maxAlt() {
        return maxAlt;
    }

    //	The array containing our height map.
    private int[][] hm;

    //	Any value less than this is undefined, but coded with a fill value.
    private int undefined;

    //	The min and max altitude in the height map.
    private int minAlt = Integer.MAX_VALUE;
    private int maxAlt = -minAlt;
}
