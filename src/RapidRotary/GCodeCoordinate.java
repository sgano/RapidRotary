/*
 * Copyright (C) 2016-2018 Shawn E. Gano, shawn@ganotechnologies.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package RapidRotary;

import java.awt.Point;
import java.util.ArrayList;

/**
 *
 * @author sgano
 */
public class GCodeCoordinate 
{
    private static double MIN_Z_RADIUS = 0.25; // units (this could be .25in or .25mm [this case is very fast] - in the future may want to take into account metric to help safegaurd this better)
    
    private double X, Y, Z, A;
    private boolean Xset, Yset, Zset, Aset;
    
    // values for arc offsets
    private double I, J;
    private boolean Iset, Jset; 
    
    // SEG v1.2 added K
    private double K;
    private boolean Kset;

    // constructor
    public GCodeCoordinate() 
    {
        iniValues();
        
    } // GCodeCoordinate - constructor
    
    // constructor -- with the current line's elements (should already be sure this is a movement line
    public GCodeCoordinate(ArrayList<String> lineParts)
    {
        // setup inital values, and flag all components as missing
        iniValues();
        
        // loop through all the parts:
        for(int i = 0; i< lineParts.size(); i++)
        {
            if(lineParts.get(i).startsWith("X"))
            {
                String value = lineParts.get(i).substring(1); // get the value after the Letter
                setX( new Double(value).doubleValue() ); // set the value
            } // if X
            else if(lineParts.get(i).startsWith("Y"))
            {
                String value = lineParts.get(i).substring(1); // get the value after the Letter
                setY( new Double(value).doubleValue() ); // set the value
            } // if Y
            else if(lineParts.get(i).startsWith("Z"))
            {
                String value = lineParts.get(i).substring(1); // get the value after the Letter
                setZ( new Double(value).doubleValue() ); // set the value
            } // if Z
            else if(lineParts.get(i).startsWith("A"))
            {
                String value = lineParts.get(i).substring(1); // get the value after the Letter
                setA( new Double(value).doubleValue() ); // set the value
            } // if A
            else if(lineParts.get(i).startsWith("I"))
            {
                String value = lineParts.get(i).substring(1); // get the value after the Letter
                setI( new Double(value).doubleValue() ); // set the value
            } // if I
            else if(lineParts.get(i).startsWith("J"))
            {
                String value = lineParts.get(i).substring(1); // get the value after the Letter
                setJ( new Double(value).doubleValue() ); // set the value
            } // if J
            else if(lineParts.get(i).startsWith("K"))
            {
                String value = lineParts.get(i).substring(1); // get the value after the Letter
                setK( new Double(value).doubleValue() ); // set the value
            } // if K
            
        } // for each element
        
    } // GCodeCoordinate constrctor with lineParts
    

    private void iniValues()
    {
        // default all zeros
        X = 0; Y= 0; Z = 0; A = 0;
        
        // default - no values set
        Xset = false;
        Yset = false;
        Zset = false;
        Aset = false;
        
        // arc offset 
        I = 0; J = 0;
        Iset = false;
        Jset = false;
    } //iniValues
    
    
    // if any unset values of this object exist, copy the values from the passed in object [if they are set within that object]
    // only works on X,Y,Z,A
    public void copyUnsetValuesfromPrevousPoint(GCodeCoordinate previousCoord)
    {
        // X
        if(this.isXset() == false && previousCoord.isXset() == true)
        {
            this.setX( previousCoord.getX() );
        }
        
        // Y
        if(this.isYset() == false && previousCoord.isYset() == true)
        {
            this.setY( previousCoord.getY() );
        }
        
        // Z
        if(this.isZset() == false && previousCoord.isZset() == true)
        {
            this.setZ( previousCoord.getZ() );
        }
        
        // A
        if(this.isAset() == false && previousCoord.isAset() == true)
        {
            this.setA( previousCoord.getA() );
        }
        
    } //copyUnsetValuesfromPrevousPoint
    
    // calculate the linear distance traveled, convert the A axis to a distance based on Z value
    // !!!! Assumes Z is zeroed on center of rotation - so Z is the radial distance
    // if "throwErrorIfDataNotSet" is true, then throw an error if "previousCoord".. 
    // line number passed in just for adding the information to the error message
    public double straightDistanceFromCoordinateConvertA2Dist(GCodeCoordinate previousCoord, double zZeroOffset, RapidRotary_GUI.DistanceUnits distUnits, boolean throwErrorIfDataNotSet, int lineNumber) throws GCodeException
    {
        double squareSumDist = 0; // this is the squared sum of all distances taveled

        // X
        if (this.isXset() && previousCoord.isXset()) // both are set
        {
            squareSumDist += Math.pow((this.getX() - previousCoord.getX()), 2.0); // distance squared
        } 
        else if( this.isXset() != previousCoord.isXset()  && throwErrorIfDataNotSet) // one is set and one is not
        {
            throw new GCodeException("G01 movement without an inital X starting point. (line: " + (lineNumber) + ")");
        }// X

        // Y
        if (this.isYset() && previousCoord.isYset()) // both are set
        {
            squareSumDist += Math.pow((this.getY() - previousCoord.getY()), 2.0); // distance squared
        } 
        else if( this.isYset() != previousCoord.isYset()  && throwErrorIfDataNotSet) // one is set and one is not
        {
            throw new GCodeException("G01 movement without an inital Y starting point. (line: " + (lineNumber) + ")");
        }// Y
        
        // Z
        if (this.isZset() && previousCoord.isZset()) // both are set
        {
            squareSumDist += Math.pow((this.getZ() - previousCoord.getZ()), 2.0); // distance squared
        } 
        else if( this.isZset() != previousCoord.isZset()  && throwErrorIfDataNotSet) // one is set and one is not
        {
            throw new GCodeException("G01 movement without an inital Z starting point. (line: " + (lineNumber) + ")");
        }// Z
        
        // A -- converted to a distance -- using largest Z for the move (since that produces the slowest move to be safe),
        // radius - is capped to be no smaller than MIN_Z_Radius
        // !!!!! most important section of the entire program --- converting rotary to linear distance!
        // Caution assumes Z-zero is set to the A-axis of rotaiton
        if (this.isAset() && previousCoord.isAset()) // both are set
        {
            // requires at least one Z value to be set:
            if (this.isZset() == false && previousCoord.isZset() == false)
            {
                throw new GCodeException("A-axis movement requires a prior Z-axis movement (for distance calculation) . (line: " + (lineNumber) + ")");
            }
            
            // use maxiumum absoulte value of Z, between start and end points
            // add any Z-zero offset that the user may have specified (this only can slow the rotation, used for isntance when setting Z=0 at the top surface instead of on the A-axis)
            double maxZ = Math.max( Math.abs( this.getZ()+zZeroOffset ) , Math.abs(previousCoord.getZ()+zZeroOffset ) );
                        
            //apply units callibration for tolerance
            double toleranceScale = 1.0; // default and value for inches
            if(distUnits == RapidRotary_GUI.DistanceUnits.MM)
            {
                toleranceScale = 25.4; // mm per inch
            }
            
            // apply limit to how small the radius is -- to keep from rotating too fast.
            if( maxZ < (MIN_Z_RADIUS * toleranceScale) )
            {
                maxZ = (MIN_Z_RADIUS * toleranceScale) ;
            }
            
            // angle rotation (absoulte difference) in degrees for this move:
            // - handle cases that cross 0 360? -- no because Z is treated as a contunous line, not a repeating 0-360 axis
            double Adiff = Math.abs( this.getA() - previousCoord.getA() );
            
            
            // Convert rotation distance to arc-lenth = R * theta  [theta in radians]
            double arcLength = maxZ * (Adiff * Math.PI / 180.0);
              
            // square arcLength and add it to the total for this move:
            squareSumDist += Math.pow(arcLength, 2.0); // distance squared
             
        } // A arc-length calculation
        else if( this.isAset() != previousCoord.isAset()  && throwErrorIfDataNotSet) // one is set and one is not
        {
            throw new GCodeException("G01 movement without an inital A-axis starting point. (line: " + (lineNumber) + ")");
        }// A
        
        return Math.sqrt(squareSumDist); // distance traveled.
        
    } //straightDistanceFromCoordinateConvertA2Dist
    
    // calculates the arc distance from the given previous coordinate TO this object, distance can be returned in the clockwise direction (or if false counter-clockwise direction)
    // does error checking -- requies this object to have X, Y, I, J as set parameters
    // SEG version 1.2 - added flag for what plan is selected (G17,18,19) - currentPlaneSelected
    //       G17 = XY plane (default)
    //       G18 = ZX
    //       G19 = YZ
    public double arcDistanceFromPreviousCoordinate(GCodeCoordinate previousCoord, boolean clockWiseDirection, RapidRotary_GUI.DistanceUnits distUnits, int lineNumber, int currentPlaneSelected) throws GCodeException
    {
        if(currentPlaneSelected == 17) // XY
        {
            // (error) requires previous X,Y to both be set - otherwise errror 
            if (!previousCoord.isXset() || !previousCoord.isYset())
            {
                throw new GCodeException("G02/G03 movement requires both X and Y to have been previously set (G17). (line: " + (lineNumber) + ")");
            } // (error if Z set on this line or if I, J are not ... or if K is specified on the line) -- just check X,Y,I,J
            else if (!this.isXset() || !this.isYset() || !this.isIset() || !this.isJset())
            {
                throw new GCodeException("G02/G03 movement requires X, Y, I, J arguments (G17).  One or more are missing.  (line: " + (lineNumber) + ")");
            }
        } // G17 error checks
        else if(currentPlaneSelected == 18) // ZX
        {
            // (error) requires previous X,Z to both be set - otherwise errror 
            if (!previousCoord.isXset() || !previousCoord.isZset())
            {
                throw new GCodeException("G02/G03 movement requires both Z and X to have been previously set (G18). (line: " + (lineNumber) + ")");
            } // (error if Y set on this line or if I, K are not ... or if J is specified on the line) -- just check X,Z,I,K
            else if (!this.isXset() || !this.isZset() || !this.isIset() || !this.isKset())
            {
                throw new GCodeException("G02/G03 movement requires X, Z, I, K arguments (G18).  One or more are missing.  (line: " + (lineNumber) + ")");
            }
        } // G18 error checks
        else if(currentPlaneSelected == 19) // YZ
        {
            // (error) requires previous Y,Z to both be set - otherwise errror 
            if (!previousCoord.isYset() || !previousCoord.isZset())
            {
                throw new GCodeException("G02/G03 movement requires both Y and Z to have been previously set (G19). (line: " + (lineNumber) + ")");
            } // (error if X set on this line or if J, K are not ... or if I is specified on the line) -- just check Y,Z,J,K
            else if (!this.isYset() || !this.isZset() || !this.isJset() || !this.isKset())
            {
                throw new GCodeException("G02/G03 movement requires Y, Z, J, K arguments (G19).  One or more are missing.  (line: " + (lineNumber) + ")");
            }
        } // G19 error checks
        
        // ---- SEG v1.2 (below) ---
        // G17 - variables (default case):  XY  X->X, Y->Y, I->I, J->J
        double prevX = previousCoord.getX();
        double prevY = previousCoord.getY();
        double currX = this.getX();
        double currY = this.getY();
        double currI = this.getI();
        double currJ = this.getJ();
        
        // G18, plane ZX  X->X, Z->Y, I->I, K->J
        if(currentPlaneSelected == 18)
        {
            // XZ
            prevX = previousCoord.getX();
            prevY = previousCoord.getZ();
            currX = this.getX();
            currY = this.getZ();
            currI = this.getI();
            currJ = this.getK();
        } // plane G18 (ZX)
        // G19, plane YZ  Y->X, Z->Y, J->I, K->J
        else if(currentPlaneSelected == 19)
        {
            // YZ
            prevX = previousCoord.getY();
            prevY = previousCoord.getZ();
            currX = this.getY();
            currY = this.getZ();
            currI = this.getJ();
            currJ = this.getK();
        } // plane G19 (YZ)

        
        // call arc length
        return arcLength(prevX, prevY, currX, currY, currI, currJ, clockWiseDirection, distUnits, lineNumber);
        
        
        // SEG Orginal code: (re located to arclength function in v1.2
        /*
        // -- okay at this point we are certain all the data needed is defined. ---
        // PUT THIS (AND ABOVE CHECKS) IN A FUNCTION! or method in GCodeCoordinate... with CW or CCW flag (and line number)
        // explination of the calculation done here:
        // http://math.stackexchange.com/questions/1162257/how-to-calculate-clock-wise-and-anti-clockwise-arc-lengths-between-two-points-on
        // -------------------------------------------
        // (1) calculate center point and calculate the radius of the circle (r) -- !! if r==0 then ERROR
        //   the center point is defined as the previouls coordinates + (I,J) 
        double centerPointX = previousCoord.getX() + this.getI();
        double centerPointY = previousCoord.getY() + this.getJ();
        double radius = Math.sqrt(this.getI() * this.getI() + this.getJ() * this.getJ());

        // quick check that this really is a circle -- make sure that the end point is also 1 radius away from the center
        // tolerance of 0.001 length units
        // this tolerance -- may be too tight for meteric... 
        // ** this check can really be removed since we rely on the source G-code being good anyways.
        double rError = Math.abs(Math.sqrt(Math.pow(this.getX() - centerPointX, 2.0)
                + Math.pow(this.getY() - centerPointY, 2.0)) - radius);

        //apply units callibration for tolerance
        double toleranceScale = 1.0; // default and value for inches
        if (distUnits == RapidRotary_GUI.DistanceUnits.MM)
        {
            toleranceScale = 25.4; // mm per inch
        }

        if (rError > (0.001 * toleranceScale))
        {
            throw new GCodeException("G02/G03 movement is not circular.  (line: " + (lineNumber) + ")");
        } // circle check

        // -------------------------------------------
        // (2) shift so that center is at the origin, XX is the starting point (shifted),  YY is the end point (shifted)
        double XX_1 = previousCoord.getX() - centerPointX; // x component of XX
        double XX_2 = previousCoord.getY() - centerPointY; // y component of YY

        double YY_1 = this.getX() - centerPointX; // x component of YY
        double YY_2 = this.getY() - centerPointY; // y component of YY

        // -------------------------------------------
        // (3) rotate so that the starting point is on the x+ axis. XX is rotated so it is on the x-axis.  When YY is rotated it becomes ZZ
        double ZZ_1 = XX_1 * YY_1 + XX_2 * YY_2; // x component of YY rotated to become ZZ
        double ZZ_2 = XX_1 * YY_2 - XX_2 * YY_1; // y component of YY rotated to become ZZ

        // -------------------------------------------
        // (4) use atan2 to find the clockwise angle [theta] from the starting point to the end point in range [0,2*pi)
        //      -- if negative may need to add 2*pi (working in radians here), if > 2*pi, subtract 2*pi
        //  think of this as if it were in polar coordinates
        double theta = Math.atan2(ZZ_2, ZZ_1);

        // shift theta to [0,2*pi)  
        if (theta < 0)
        {
            theta += 2.0 * Math.PI;
        } // if theta is < 0, add 2*pi
        
        if (theta >= 2.0 * Math.PI)
        {
            theta -= 2.0 * Math.PI;
        } // if theta is >= than 2*pi, shift it back down

        // -------------------------------------------
        // (5) clockwise arclength = r*theta, counter-clockwise arclenght = r*(2*pi-theta)
        //  special CNC case -- neither result should be 0, if the start and stop points are the same one of these 
        //  will normall be zero - but for the CNC case it means we want to arc an entire circle, so the arclenght is never zero   
        double clockwiseArcLenth = radius * (theta == 0 ? 2.0 * Math.PI : theta);
        double counterClockwiseArcLength = radius * (2.0 * Math.PI - (theta >= 2.0 * Math.PI ? 0 : theta));

        //need to test cases for full circle in both CCW and CW mode, as well as partial arcs
        // return the arc length from the desired direction:
        if (clockWiseDirection == true)
        {
            return clockwiseArcLenth;
        } 
        else
        {
            return counterClockwiseArcLength;
        }
        */
        
    } // arcDistanceFromPreviousCoordinate

    // SEG v1.2
    private static double arcLength(double prevX, double prevY, double currX, double currY, double currI, double currJ, boolean clockWiseDirection, RapidRotary_GUI.DistanceUnits distUnits, int lineNumber) throws GCodeException
    {
        // -- okay at this point we are certain all the data needed is defined. ---
        // PUT THIS (AND ABOVE CHECKS) IN A FUNCTION! or method in GCodeCoordinate... with CW or CCW flag (and line number)
        // explination of the calculation done here:
        // http://math.stackexchange.com/questions/1162257/how-to-calculate-clock-wise-and-anti-clockwise-arc-lengths-between-two-points-on
        // -------------------------------------------
        // (1) calculate center point and calculate the radius of the circle (r) -- !! if r==0 then ERROR
        //   the center point is defined as the previouls coordinates + (I,J) 
        double centerPointX = prevX + currI;
        double centerPointY = prevY + currJ;
        double radius = Math.sqrt(currI * currI + currJ * currJ);

        // quick check that this really is a circle -- make sure that the end point is also 1 radius away from the center
        // tolerance of 0.001 length units
        // this tolerance -- may be too tight for meteric... 
        // ** this check can really be removed since we rely on the source G-code being good anyways.
        double rError = Math.abs(Math.sqrt(Math.pow(currX - centerPointX, 2.0)
                + Math.pow(currY - centerPointY, 2.0)) - radius);

        //apply units callibration for tolerance
        double toleranceScale = 1.0; // default and value for inches
        if (distUnits == RapidRotary_GUI.DistanceUnits.MM)
        {
            toleranceScale = 25.4; // mm per inch
        }

        if (rError > (0.001 * toleranceScale))
        {
            throw new GCodeException("G02/G03 movement is not circular.  (line: " + (lineNumber) + ")");
        } // circle check

        // -------------------------------------------
        // (2) shift so that center is at the origin, XX is the starting point (shifted),  YY is the end point (shifted)
        double XX_1 = prevX - centerPointX; // x component of XX
        double XX_2 = prevY - centerPointY; // y component of YY

        double YY_1 = currX - centerPointX; // x component of YY
        double YY_2 = currY - centerPointY; // y component of YY

        // -------------------------------------------
        // (3) rotate so that the starting point is on the x+ axis. XX is rotated so it is on the x-axis.  When YY is rotated it becomes ZZ
        double ZZ_1 = XX_1 * YY_1 + XX_2 * YY_2; // x component of YY rotated to become ZZ
        double ZZ_2 = XX_1 * YY_2 - XX_2 * YY_1; // y component of YY rotated to become ZZ

        // -------------------------------------------
        // (4) use atan2 to find the clockwise angle [theta] from the starting point to the end point in range [0,2*pi)
        //      -- if negative may need to add 2*pi (working in radians here), if > 2*pi, subtract 2*pi
        //  think of this as if it were in polar coordinates
        double theta = Math.atan2(ZZ_2, ZZ_1);

        // shift theta to [0,2*pi)  
        if (theta < 0)
        {
            theta += 2.0 * Math.PI;
        } // if theta is < 0, add 2*pi
        
        if (theta >= 2.0 * Math.PI)
        {
            theta -= 2.0 * Math.PI;
        } // if theta is >= than 2*pi, shift it back down

        // -------------------------------------------
        // (5) clockwise arclength = r*theta, counter-clockwise arclenght = r*(2*pi-theta)
        //  special CNC case -- neither result should be 0, if the start and stop points are the same one of these 
        //  will normall be zero - but for the CNC case it means we want to arc an entire circle, so the arclenght is never zero   
        double clockwiseArcLenth = radius * (theta == 0 ? 2.0 * Math.PI : theta);
        double counterClockwiseArcLength = radius * (2.0 * Math.PI - (theta >= 2.0 * Math.PI ? 0 : theta));

        //need to test cases for full circle in both CCW and CW mode, as well as partial arcs
        // return the arc length from the desired direction:
        if (clockWiseDirection == true)
        {
            return clockwiseArcLenth;
        } 
        else
        {
            return counterClockwiseArcLength;
        }
        
    } // arcLength(...)  
     
    
    //A-axis rotation between two coordinates (absolute value) -- if either (or neither) have an A set returns 0;
    public double rotationAbsoluteDifferenceDegrees(GCodeCoordinate previousCoord)
    {
        if (this.isAset() == false || previousCoord.isAset() == false)
        {
            return 0;
        }
        
        return Math.abs( this.getA() - previousCoord.getA() );
        
    } //rotationAbsoluteDifferenceDegrees
    
    public double getX() 
    {
        return X;
    }

    public void setX(double X) 
    {
        this.X = X;
        this.Xset = true;
    }

    public double getY() 
    {
        return Y;
    }
    
    public void setY(double Y) 
    {
        this.Y = Y;
        this.Yset = true;
    }

    public double getZ() 
    {
        return Z;
    }
    
    public void setZ(double Z) 
    {
        this.Z = Z;
        this.Zset = true;
    }

    public double getA() 
    {
        return A;
    }
    
    public void setA(double A) 
    {
        this.A = A;
        this.Aset = true;
    }

   

    public boolean isXset() 
    {
        return Xset;
    }

    public boolean isYset() 
    {
        return Yset;
    }

    public boolean isZset() 
    {
        return Zset;
    }

    public boolean isAset() 
    {
        return Aset;
    }
    
    // arc coordinates

    public void setI(double I) 
    {
        this.I = I;
        this.Iset = true;
    }
    
    public double getI() 
    {
        return I;
    }

    public void setJ(double J) 
    {
        this.J = J;
        this.Jset = true;
    }
    
    public double getJ() 
    {
        return J;
    }

    public boolean isIset() 
    {
        return Iset;
    }

    public boolean isJset() 
    {
        return Jset;
    }
    
    // SEG v1.2 -----
    public void setK(double K) 
    {
        this.K = K;
        this.Kset = true;
    }
    
    public double getK() 
    {
        return K;
    }
 
    public boolean isKset() 
    {
        return Kset;
    }
    
    
} // GCodeCoordinate class
