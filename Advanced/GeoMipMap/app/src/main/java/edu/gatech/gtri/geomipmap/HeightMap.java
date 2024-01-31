package edu.gatech.gtri.geomipmap;

/**
 *  This is the interface in common to all height map implementations.  A height map
 *  is a rectangular grid of altitude (height) values where the spacing between grid
 *  points is constant and can be represented by integer indices.
 *
 *  <p>  Modified by:  Joseph A. Huwaldt   </p>
 *
 *  @author  Joseph A. Huwaldt   Date:  April 26, 2001
 *  @version July 24, 2004
 **/
public interface HeightMap
{
    /**
     *  Returns the altitude at the given horizontal and vertical position
     *  in the height map array.
     *
     *  @param  h   The horizontal position the height array.
     *  @param  v   The vertical position in the height array.
     *  @return The altitude value at the specific location in the height map.
     **/
    public float get(int h, int v);

    /**
     *  Returns the height of this altitude map.
     **/
    public int getHeight();

    /**
     *  Returns the width of this altitude map.
     **/
    public int getWidth();

    /**
     *  Returns the minimum altitude in the height map.
     **/
    public float minAlt();

    /**
     *  Returns the maximum altitude in the height map.
     **/
    public float maxAlt();
}
