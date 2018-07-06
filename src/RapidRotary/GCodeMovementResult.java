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

/**
 *
 * @author sgano
 */
public class GCodeMovementResult 
{
    public boolean isMovementLine;  // if the line anaylsed is a movement
    public boolean isImplicitMovement; // if the line is a movement, is it an implicit move? (if false then the G code is speficied on this line)
    public int gCodeMovementType;   // if the move is NOT implicit, this int will contain the gcode value {0,1,2,3}

    // constructor
    public GCodeMovementResult(boolean isMovementLine, boolean isImplicitMovement, String gCodeString) 
    {
        this.isMovementLine = isMovementLine;
        this.isImplicitMovement = isImplicitMovement;
        
        if(isMovementLine == true && isImplicitMovement == false)
        {
            setGCode(gCodeString);
        }
        else
        {
            gCodeMovementType = -1; // not valid
        }
        
    } // GCodeMovementResult constructor
    
     public void setGCode(String gCodeString)
     {
         if(gCodeString.length() < 2)
         {
             // error
             this.gCodeMovementType = -1; //invalid
         }
         
         // get string ignoring the leading "G" 
         String gCodeStr = gCodeString.substring(1);
        // convert to int
        this.gCodeMovementType = new Integer(gCodeStr).intValue();
        
     } // setGCode
    
} // GCodeMovementResult class
