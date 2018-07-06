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
 * @author Shawn E. Gano, shawn@ganotechnologies.com
 */
public class GCodeException extends Exception
{
      //Parameterless Constructor
      public GCodeException() {}

      //Constructor that accepts a message
      public GCodeException(String message)
      {
         super(message);
      }
      
      // constructor for exception stringing
      public GCodeException(Throwable th)
      {
          super(th);
      }
      
      // constructor for exception stringing
      public GCodeException(String message, Throwable th)
      {
          super(message, th);
      }
      
} // GCodeException class
