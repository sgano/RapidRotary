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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 *
 * @author Shawn E. Gano, shawn@ganotechnologies.com
 */
public class GlobalFileConverterSwingWorker extends SwingWorker<Object, String>
{

    private JTextArea messageTextArea;
    private RapidRotary_GUI callbackObject; // use this object to signal when done
    private long startTime; // nanoSeconds
    private String inputFilePath;
    private String outputFilePath;
    private String appVersionString;
    private double zZeroOffset;
    private RapidRotary_GUI.ConversionMode conversionMode;
    private RapidRotary_GUI.DistanceUnits distUnits;

    //  ini error flags and messages
    String errorMessage;
    boolean errorProcessingFile;
    int linesProcessed = 0;
    boolean endOfProgramFound = false;
    boolean startProgramPercentSignFound = false;

    // G-code parseing parameters
    GCodeCoordinate lastCoordinate;
    double currentFeedRate = -1; // -1 means not set
    int lastGCode = -1;
    boolean G94Found = false;
    boolean G17_18_19_Found = false; // SEG v1.2 changed from G17Found only (Plane Select)
    boolean G90Found = false;
    int currentPlaneSelected = 17; // SEG v1.2 added this to track current (should be either 17,18, or 19 only) G17=XY, G18=ZX, G19=YZ

    double totalToolPathDistance = 0;
    double totalDegreesRotated = 0;
    long totalG00lines = 0;
    long totalG01lines = 0;
    long totalG02lines = 0;
    long totalG03lines = 0;

    boolean rotaryMinMaxFound = false; // flag if the min max have been set yet
    double rotaryMax = 0;
    double rotaryMin = 0;
    int numberLinesWithRotaryMoves = 0; // count number of lines that specifiy rotary (a-axis) moves [If A is specified but not changed it doesn't count]

    // SEG v1.1
    int fOutputPrecision = 5; // number of digits after the decimal to write for the F values, default is 5
    
    // SEG v1.1 added fPrecision
    GlobalFileConverterSwingWorker(String inFilePath, String outFilePath, double zZeroOffsetInput, RapidRotary_GUI.ConversionMode convMode,
            RapidRotary_GUI.DistanceUnits dUnits, int fPrecision, JTextArea textArea, RapidRotary_GUI callbackObj, String appVer)
    {
        //initialize / save values
        inputFilePath = inFilePath;
        outputFilePath = outFilePath;
        messageTextArea = textArea;
        callbackObject = callbackObj;
        appVersionString = appVer;
        zZeroOffset = zZeroOffsetInput;
        conversionMode = convMode;
        distUnits = dUnits;
        fOutputPrecision = fPrecision;

    } // constructor

    private void iniDataBeforeProcessing()
    {
        //  ini error flags and messages
        errorMessage = "";
        errorProcessingFile = false;
        linesProcessed = 0;
        endOfProgramFound = false;
        startProgramPercentSignFound = false;

        lastCoordinate = new GCodeCoordinate(); // create new emtpy coordinate

        currentFeedRate = -1; // -1 means not set
        lastGCode = -1;
        G94Found = false;
        G17_18_19_Found = false; // SEG v1.2 update to new name
        G90Found = false;
        totalToolPathDistance = 0;
        totalDegreesRotated = 0;

        totalG00lines = 0;
        totalG01lines = 0;
        totalG02lines = 0;
        totalG03lines = 0;

        rotaryMinMaxFound = false; // flag if the min max have been set yet
        rotaryMax = 0;
        rotaryMin = 0;
        numberLinesWithRotaryMoves = 0;

        return;
    } //iniDataBeforeProcessing

    // --- SUPPORT NOTES ---
    // need to scan and give error if G93 ever found
    // Error if any G18 (ZX Plane for arcs) or G19 (YZ Plane - for arcs) ever found
    // error if G94, G17, and G90 have all not been found before G01,G02, or G03 are called.
    // error if feedrate not specified before a G01, G02, G03 is first called
    // if a '(' is found the rest of the line is ignored even after ')'
    // R command for G02 and G03 not supported
    // G02 and G03 -- do we support multiple quarants for a single command??
    // -- will swap G94 command(s) to G93 in file
    // remove all feedrates from orginal... or add them to comments!)
    // requires all capital letters G,F,X,Y,Z,A
    // this is where the work is completed
    @Override
    protected Object doInBackground() throws Exception
    {
        // create start time
        startTime = System.nanoTime();

        // clear/reset all variables
        iniDataBeforeProcessing();

        // count lines in input file -- so progress can be updated
        long totalLinesInFile = countLinesInFile(inputFilePath);
        publish("Total lines in input file: " + totalLinesInFile);

        // main try loop for processing the files
        try
        {
            // create output file (overwrite if it already exists)
            File file = new File(outputFilePath);
            file.getParentFile().mkdirs(); // if the path to the file doesn't exists, make it!
            PrintWriter outputFilePrintWriter = new PrintWriter(file);

            //publish these to message area too
            publish("(-------------------------------------------------------------)");
            publish("( WARNING: Review and test this program for your machine and setup.)");
            publish("(                 Use the converted G-code at your own risk                  )");
            publish("(-------------------------------------------------------------)");

            // read and process each line of the input file:
            BufferedReader inFileReader = new BufferedReader(new FileReader(inputFilePath));
            String nextLine = inFileReader.readLine();
            linesProcessed = 0;
            while (nextLine != null)
            {

                // process line: (if there is an error it will throw an exception)
                String line2Write = processInputLine(nextLine);

                // write output line to output file:
                outputFilePrintWriter.println(line2Write);

                // count lines processed
                linesProcessed++;

                //java.lang.Thread.sleep(10); // delay for debugging
                // set progress
                int progress = (int) Math.round(((linesProcessed * 1.0) / (totalLinesInFile * 1.0)) * 100.0);
                setProgress(progress);

                nextLine = inFileReader.readLine(); // read the next line
            } // while each line from input file is not null

            // close files
            inFileReader.close(); // close input file
            outputFilePrintWriter.close(); // close output file

        } // try block for processing file
        catch (Exception e)
        {
            publish("\nERROR PROCESSING FILE: " + e.toString());
            errorMessage = "Error Processing File (see message area for details):\n\n" + e.getLocalizedMessage();
            errorProcessingFile = true;

            // delete output file so it isn't accidentally used!           
            try
            {
                File file2Delete = new File(outputFilePath);
                file2Delete.delete();

            } catch (Exception x)
            {
                publish("Error deleting output file after error: " + e.getLocalizedMessage());
            }// catch from deleting output file after an error

        } // catch for processing files

        // if error processing:
        if (errorProcessingFile == true)
        {
            // show alert on main thread
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {

                    // create and show a swing item, eg.:
                    JOptionPane.showMessageDialog(callbackObject,
                            errorMessage,
                            "Error Processing File",
                            JOptionPane.ERROR_MESSAGE);

                }
            }); // alert on main thread

        } // display error
        else if (endOfProgramFound == false)
        {
            String warningStr = "WARNING: no end of program found (%, M2, or M30). A G94 command was not inserted at the end of the output file. ";

            // show alert on main thread
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    JOptionPane.showMessageDialog(callbackObject,
                            warningStr,
                            "Warning",
                            JOptionPane.WARNING_MESSAGE);
                }
            }); // alert on main thread

            publish(warningStr);

        } // check if the end of the program was found // no error found in processing

        // You could return the down load file if you wanted...
        return null;

    } //doInBackground

    // count the lines in a file
    private long countLinesInFile(String pathToFile)
    {
        long totalLines = 0;
        try
        {
            BufferedReader reader = new BufferedReader(new FileReader(pathToFile));
            while (reader.readLine() != null)
            {
                totalLines++;
            }
            reader.close();
        } catch (Exception ignore)
        {
            publish("Error counting lines in input file: " + ignore.toString() + "\n");
        }

        return totalLines;

    } // countLinesInFile

    // process the line, returns if successfull and the line to write out
    // if there is an error this will throw a exception
    private String processInputLine(String lineIn) throws GCodeException
    {

        // stores the comments on the line if there are any comments
        String lineComments = "";
        int commentStart = lineIn.indexOf("(");
        if (commentStart >= 0)
        {
            // save comment
            lineComments = lineIn.substring(commentStart);

            // remove comment from the line
            lineIn = lineIn.substring(0, commentStart);

        }// if there is a comment on this line

        // split break up the line into tolkens/parts based on spaces
        String[] splitParts = lineIn.split("\\s+"); // split on white space or the comment start of '('
        ArrayList<String> lineParts = new ArrayList<String>(Arrays.asList(splitParts));

        // trim each element - to make sure there is no extra whitespace
        for (int i = 0; i < lineParts.size(); i++)
        {
            lineParts.set(i, lineParts.get(i).trim());
        }

        // scan for a feedrate change..
        // SEG v1.0.1 -- added return of Feed String (so it can be added if in wrap only A mode and we need to put it back)
        String feedString = processFeedRatesIfAny(lineParts);

        // scan for non-moving G codes that are of interest (for error checking or replacing like G94->G93)
        processNonMovingGCodesOfInterest(lineParts);

        // scan for G00, G01, G02, G03 codes and movements or implicity move lines and process them (add G93 F values too)
        boolean moveContainsRotaryChangeAndNotG00 = processMovingGCodesAndImplicitMoves(lineParts);

        // add G94 to the end of program // Every G code file must end in a M2 or M30 or be wrapped with the percent sign %.
        boolean wasStartFoundOnLine = processStartStopOfProgramAddG94ToEnd(lineParts);

        // **** re-combine all the parts of the line **** 
        String returnLine = "";
        for (String str : lineParts)
        {
            // add part, if there is anything there (this keeps from adding unneeded spaces)
            if (str.length() > 0)
            {
                if (returnLine.length() > 0)
                {
                    returnLine = returnLine + " " + str;
                }// space needed between parts
                else // no content yet, so just add str and no preceeding space
                {
                    returnLine = returnLine + str;
                } // else no space needed

            }// if the part has text
        } // for each line part
        
        // SEG v1.0.1 -- if wrap only A mode, and we aren't in an A-move add feed rates back as needed
        if (conversionMode == RapidRotary_GUI.ConversionMode.WRAP_EACH_A_MOVE && moveContainsRotaryChangeAndNotG00 == false)
        {
            if(feedString.length() > 0)
            {
                returnLine += (" " + feedString);
            }
        } // if we need to add the feed rate back to the line
        

        // add the comments (add an extra space if needed)
        if (returnLine.length() > 0 && !returnLine.endsWith(" ") && lineComments.length() > 0)
        {
            returnLine = returnLine + " " + lineComments;
        } else // doesn't need a 
        {
            returnLine = returnLine + lineComments;
        }

        // if the start was found on this line, then add header afterwards. as the % must be first before any comments in the file
        if (wasStartFoundOnLine == true)
        {
            // create time stamp string:
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");
            Date date = new Date();
            String timeStampString = dateFormat.format(date);

            // conversion mode string
            String conversionModeStr = "?";
            if (conversionMode == RapidRotary_GUI.ConversionMode.WRAP_ALL)
            {
                conversionModeStr = "Use G93 mode for entire file";
            } else if (conversionMode == RapidRotary_GUI.ConversionMode.WRAP_EACH_A_MOVE)
            {
                conversionModeStr = "Wrap each rotary move in G93";
            }

            // distance units string
            String distUnitsStr = "?";
            if (distUnits == RapidRotary_GUI.DistanceUnits.INCHES)
            {
                distUnitsStr = "inches";
            } else if (distUnits == RapidRotary_GUI.DistanceUnits.MM)
            {
                distUnitsStr = "mm";
            }

            // add header to the top of the file (after the start %):
            returnLine += "\n(-------------------------------------------------------------)";
            returnLine += "\n(       Converted G-code from G94 to G93 Invserse Time        )";
            returnLine += "\n(       Rapid Rotary " + appVersionString + ", shawn@ganotechnologies.com       )";
            returnLine += "\n( WARNING: Review and test program for your machine and setup.)";
            returnLine += "\n(          Use this program at your own risk                  )";
            returnLine += "\n(                    " + timeStampString + "                     )";
            returnLine += "\n(       Z-zero offset from A-axis: " + zZeroOffset + "                        )";
            returnLine += "\n(       Conversion Mode: " + conversionModeStr + "         )";
            returnLine += "\n(       Distance Units: " + distUnitsStr + "                                )";
            returnLine += "\n(       F value output decimal digits : " + fOutputPrecision + "                     )"; // SEG v1.1
            returnLine += "\n(-------------------------------------------------------------)";
            publish("- Preamble added to output file");
        } // if wasStartFoundOnLine == true

        if (conversionMode == RapidRotary_GUI.ConversionMode.WRAP_EACH_A_MOVE && moveContainsRotaryChangeAndNotG00)
        {
            // add 93 to the front of the line and then afterwards add G94 and Feedrate, add extra line breaks around this wrapping for better readability
            returnLine = "\nG93\n" + returnLine + "\nG94\nF" + currentFeedRate + "\n";

        } // if wrapping just A moves mode is selected, and the current line contains an A move (but not a G0 A move)
        //Do implicit moves still work between switching modes?

        return (returnLine);

    } //processInputLine

    // this modifies the input ArrayList as needed, and extracts any feedrate information and saves it to the class variables
    // SEG v1.0.1 -- returns the feed rate strings in case they need to be added back - if in wrap only A moves mode
    private String processFeedRatesIfAny(ArrayList<String> lineParts)
    {
        String feedStrings = "";
        
        for (int i = 0; i < lineParts.size(); i++)
        {
            if (lineParts.get(i).startsWith("F"))
            {
                String feedStr = lineParts.get(i).substring(1);
                // convert to double
                this.currentFeedRate = new Double(feedStr).doubleValue();
                
                // SEG v1.0.1 add this string to the Feed String
                feedStrings += (lineParts.get(i) + " ");

                // for debug and verification:
                //publish("- New feedrate of " + this.currentFeedRate + " found at line " + (this.linesProcessed+1) );
                // erase this Feed rate from the array, since we can't use old feedrates in G93
                lineParts.set(i, "");

            } // this is a F (feed rate) entry

        }  // for each element
        
        // SEG v1.0.1
        return feedStrings;

    } //processFeedRatesIfAny

    private void processNonMovingGCodesOfInterest(ArrayList<String> lineParts) throws GCodeException
    {
        // need to scan and give error if G93 ever found
        // **Error if any G18 (ZX Plane for arcs) or G19 (YZ Plane - for arcs) ever found
        // **--- Version 1.2 allows G18 and G19 now
        // error if G94, G17, and G90 have all not been found before G01,G02, or G03 are called.
        for (int i = 0; i < lineParts.size(); i++)
        {
            if (lineParts.get(i).startsWith("G"))
            {
                String gCodeStr = lineParts.get(i).substring(1);
                // convert to int
                int gCode = new Integer(gCodeStr).intValue();

                if (gCode == 94) // G94 (Feed Rate Mode: units per minute)
                {
                    // Mark as found!
                    G94Found = true;

                    // change to G93! (if in wrap all mode - otherwise leave it alone)
                    if (conversionMode == RapidRotary_GUI.ConversionMode.WRAP_ALL)
                    {
                        lineParts.set(i, "G93 (Inverse Time Mode, Converted from G94)");
                    }

                    // publish find
                    publish("- G94 (Feed Rate Mode: units per minute)  FOUND! Line =  " + (this.linesProcessed + 1));
                } // G94
                else if (gCode == 93) // G94 (Inverse Feed Rate Mode: units per minute) - ERROR can't convert back!!
                {
                    throw new GCodeException("G93 Found in input file.  Retaining inverse time mode within input file is not supported. (line " + (this.linesProcessed + 1) + ")");
                } // G93
                else if (gCode == 17) // G17 (Plane Select: XY)
                {
                    // Mark as found!
                    G17_18_19_Found = true; // SEG v1.2
                    currentPlaneSelected = 17; // set current plane

                    // publish find
                    publish("- G17 (Plane Select: XY)  FOUND! Line =  " + (this.linesProcessed + 1));
                } // G17
                else if (gCode == 18) // G18 (ZX Plane for arcs)
                {
                    // Mark as found!
                    G17_18_19_Found = true; // SEG v1.2
                    currentPlaneSelected = 18; // set current plane

                    // publish find
                    publish("- G18 (Plane Select: ZX)  FOUND! Line =  " + (this.linesProcessed + 1));
                    
                    //throw new GCodeException("G18 (ZX Plane for arcs) found in input file.  Only arcs in XY plane (G17) are supported. (line " + (this.linesProcessed + 1) + ")");
                } // G18 
                else if (gCode == 19) //G19 (YZ Plane - for arcs)
                {
                    // Mark as found!
                    G17_18_19_Found = true; // SEG v1.2
                    currentPlaneSelected = 19; // set current plane

                    // publish find
                    publish("- G19 (Plane Select: YZ)  FOUND! Line =  " + (this.linesProcessed + 1));
                    
                    //throw new GCodeException("G19 (YZ Plane for arcs) found in input file.  Only arcs in XY plane (G17) are supported. (line " + (this.linesProcessed + 1) + ")");
                } // G19
                else if (gCode == 90) // G90 (Set absolute distance mode)
                {
                    // Mark as found!
                    G90Found = true;

                    // publish find
                    publish("- G90 (Set absolute distance mode)  FOUND! Line =  " + (this.linesProcessed + 1));
                } // G90
                

            } // this is a G-code entry

        }  // for each element

    } //processNonMovingGCodesOfInterest

    // scan for G00, G01, G02, G03 codes and movements or implicity move lines and process them (add G93 F values too)
    // returns a boolean if there was a rotary move that was not a G00
    private boolean processMovingGCodesAndImplicitMoves(ArrayList<String> lineParts) throws GCodeException
    {
        boolean moveContainsRotaryChangeAndNotG00 = false;

        // check if this line is a move command
        // determine if this is a move... look for parts that start with G0, G00, G01, G1, G02, G2, G02, X, Y, Z, A -- then it is a move or implicit move! 
        GCodeMovementResult moveCommandResult = checkForLineMovementCommands(lineParts);

        // if not a move line - return
        if (moveCommandResult.isMovementLine == false)
        {
            return false; // not a movement so return (moveContainsRotaryChangeAndNotG00 == false)
        } // checking for movement

        // check if move seems implicit but no last "G0/1/2/3" was set
        if (moveCommandResult.isImplicitMovement == true && lastGCode < 0)
        {
            // error, if implicit move before a movement Gcode is specified
            throw new GCodeException("Implicit move command given before listing an actual move command (G0/1/2/3). "
                    + "(line: " + (this.linesProcessed + 1 + ")")
            );
        }

        // --- since this is a move command figure out which one it is:
        int currentMoveGCode = -1;
        if (moveCommandResult.isImplicitMovement == true)
        {
            // implicit - use last command type
            currentMoveGCode = this.lastGCode;
        } else // it was specified in the current line (get form result)
        {
            currentMoveGCode = moveCommandResult.gCodeMovementType;
        }

        // save the last command for next time around
        this.lastGCode = currentMoveGCode;

        // since this is a move -- be sure G90,G17, G94 are all set and a feedrate has been set -- otherwise ERROR
        // unless this is G0 then don't give an error yet
        if (currentMoveGCode != 0)
        {
            if (this.currentFeedRate < 0 || G94Found == false || G17_18_19_Found == false || G90Found == false) // SEG v1.2 updated G17_18 name
            {
                // SEG v1.2 - added "not" before set on each item
                throw new GCodeException("Movement G-code (G01,G02,G03) found before the following requirements were set: "
                        + ((this.currentFeedRate < 0) ? "\nFeedrate not set" : "")
                        + ((G17_18_19_Found == false) ? "\nG17, G18, or G19 (plane) not set" : "") // SEG v1.2 added G18 and G19
                        + ((G90Found == false) ? "\nG90 not set" : "")
                        + ((G94Found == false) ? "\nG94 not set" : "")
                        + "\nline: " + (this.linesProcessed + 1));

            } // error for movement command
        } // not G00 or G0 -- inside is the check for needed modes settings and feedrate set

        // read in this line's coordinate parameters from this line: (X,Y,Z,A,I,J)
        // this is the new coordinate and represents the end point for the next move
        GCodeCoordinate endCoordinate = new GCodeCoordinate(lineParts);

        // copy unset values on this line from previous point... unless that isn't set (then don't set it)...
        // works on X, Y, Z, A  (not I or J)
        endCoordinate.copyUnsetValuesfromPrevousPoint(lastCoordinate);

        double distanceTraveled = -1; // value for the distance traveled for this line, -1 if not determinable (i.e. first move in this direction)
        double degreesRotated = -1; // total degrees rotated this line
        boolean appendFvalueToLine = false;

        // If G00 (or implicit) -- save location and save Gcode  , no need to change anything on the line, then return 
        //                          save distance moved too -- if not first move
        //   Add new variable for total distance of rapid moves, vs straight, vs two cruve moves,,,
        //  --- don't calculate distance for an axis if there wasn't a previous move recorded in that axis before this one... just subsequent moves
        if (currentMoveGCode == 0) // linear move (fast)
        {
            totalG00lines++;

            // -- no errors of there were no previous data or current data to move to - as G0 may be the first moves made in each axis
            // calulate the distance traveled (if both start and endpoint exist for that axis) - ignore moves that don't
            // includ rotart axis A rotations in distance converted using Z as the radius value (assumes Z-0 is on A axis)
            distanceTraveled = endCoordinate.straightDistanceFromCoordinateConvertA2Dist(lastCoordinate, zZeroOffset, distUnits, false, this.linesProcessed + 1);

            degreesRotated = endCoordinate.rotationAbsoluteDifferenceDegrees(lastCoordinate);

            // used to debug / verify results
            //publish("- line: " + (this.linesProcessed + 1) + ":  distance traveled =  "+ distanceTraveled +", degRot = " + degreesRotated );
            // no need for F values for G0 commands
            appendFvalueToLine = false;

        } // G00
        // if G01 (or implicit) [linear move] -- save location and gCode, determind distance, add F inverse time after all X,Y,Z,A parameters
        // (error) requires some delta distance from previous point (at least one set before)-- so distance can be calculated for the move!
        // (error) also requires that a move in any set endpoint, means the last point must have been set in that axis (meaning this can't represent the first move for an axis)
        else if (currentMoveGCode == 1) // linear move (at set feed rate)
        {
            totalG01lines++;
            // calulate the distance traveled 
            // includd rotarty axis A rotations in distance converted using Z as the radius value (assumes Z-0 is on A axis)
            // throw exception if any move doesn't have a previous value set -- (because distance can't be calculted)
            distanceTraveled = endCoordinate.straightDistanceFromCoordinateConvertA2Dist(lastCoordinate, zZeroOffset, distUnits, true, this.linesProcessed + 1);

            degreesRotated = endCoordinate.rotationAbsoluteDifferenceDegrees(lastCoordinate);

            // used to debug / verify results
            //publish("- line: " + (this.linesProcessed + 1) + ":  distance traveled =  "+ distanceTraveled +", degRot = " + degreesRotated );
            // add F values for G01 commands
            appendFvalueToLine = true;

        } // G01
        // if G02    [circular move clockwise] - in X-Y plane only (since G17 is active)
        // (error if Z set on this line or if I, J, Y, X are not ... or if K is specified on the line)
        // (error if A is set on this line!
        else if (currentMoveGCode == 2) // arc path - clockwise
        {
            totalG02lines++;

            // calculate arc-distance from last coordinate to the endCoordinate, clockwise (true)
            // SEG v1.2 - added input for which plane is currently selected (G17,18,19)
            distanceTraveled = endCoordinate.arcDistanceFromPreviousCoordinate(lastCoordinate, true, distUnits, this.linesProcessed + 1, currentPlaneSelected);

            // used to debug / verify results
            //publish("- G02 line: " + (this.linesProcessed + 1) + ":  distance traveled =  "+ distanceTraveled);
            // no A-axis rotation supported in G02 (for this converter)
            degreesRotated = 0;

            // add F values for G02 commands
            appendFvalueToLine = true;

            // (error if A is set on this line!
        } // G02
        // if G03    [circular move counter-clockwise] - in X-Y plane only (since G17 is active)
        // (error) requires previous X,Y to both be set - otherwise error
        // (error if Z set on this line or if I, J are not ... or if K is specified on the line)
        // (error if A is set on this line!
        else if (currentMoveGCode == 3) // arc path -- counter-clockwise
        {
            totalG03lines++;
            // (error if Z set on this line or if I, J are not ... or if K is specified on the line)

            // calculate arc-distance from last coordinate to the endCoordinate, counter-clockwise (false)
            // SEG v1.2 - added input for which plane is currently selected (G17,18,19)
            distanceTraveled = endCoordinate.arcDistanceFromPreviousCoordinate(lastCoordinate, false, distUnits, this.linesProcessed + 1, currentPlaneSelected);

            // used to debug / verify results
            //publish("- G03 line: " + (this.linesProcessed + 1) + ":  distance traveled =  "+ distanceTraveled);
            // no A-axis rotation supported in G03 (for this converter)
            degreesRotated = 0;

            // add F values for G03 commands
            appendFvalueToLine = true;

            // (error if A is set on this line!
        } // G03

        // --- scan to see if there was an A specified on this line, deal with it specially if in "wrap only A moves"
        //      also add it to the count
        //NO move to later... just check to see if it changed (or didn't exist and now it does)..  only need it when A moves!
        //-- but also G93 not needed for G0 moves.
        if (endCoordinate.isAset() == true && lastCoordinate.isAset() == false)
        {
            // first move -- which may be to 0-deg so we need to count it specially
            this.numberLinesWithRotaryMoves++;

            // this actually should never happen, if there was no previous A-axis set, but I guess it is theorectically possible
            if (currentMoveGCode != 0)
            {
                moveContainsRotaryChangeAndNotG00 = true;
            }

        } else if (endCoordinate.isAset() == true && endCoordinate.getA() != lastCoordinate.getA())
        {
            // increment rotary moves
            this.numberLinesWithRotaryMoves++;

            if (currentMoveGCode != 0)
            {
                moveContainsRotaryChangeAndNotG00 = true;
            }

        } // there was a change in A-axis movement

        // this is a check -- based on the conversion method if we really want to add F to the line
        if (appendFvalueToLine)
        {
            if (conversionMode == RapidRotary_GUI.ConversionMode.WRAP_EACH_A_MOVE)
            {
                if (moveContainsRotaryChangeAndNotG00 != true)
                {
                    appendFvalueToLine = false; // we don't need to write the F rate in this case
                }

            } // if wrap each A-move mode is selected

        } // if appedFvaluesToLine -- check for modes, may have to turn this off if needed

        // if appendFvalueToLine  -- calculate time needed and inverse time value, add to end of the line parameters!
        if (appendFvalueToLine)
        {

            if (this.currentFeedRate <= 0)
            {
                throw new GCodeException("Feedrate must be positive and non-zero (line: " + (this.linesProcessed + 1) + ")");
            }

            // calcuate the time needed to move the distance traveled in this step
            // distanceTraveled  [dist]
            //this.currentFeedRate  [dist/min]
            double timeForStepInMinutes = distanceTraveled / this.currentFeedRate;

            // notes: http://www.cnczone.com/forums/haas-mills/69433-mastercam.html
            // FRN = Feed rate number in inverse time
            // TIME = The time in minutes to move from A to B 
            // FRN = 1 / TIME
            double frn = 0; // default
            if (timeForStepInMinutes > 0) // protect from divide by zero
            {
                frn = 1.0 / timeForStepInMinutes;
            } else
            {
                publish("WARNING: zero distance, zero speed, or infinte speed for move on line: " + (this.linesProcessed + 1));
            } // else caught a divide by zero
           
            // SEG v1.1 - customizable 
            String fFormatPrecisionStr = "%." + fOutputPrecision + "f";
            
            //String frnStr = String.format("%.5f", frn); // format decimal to 5 place including zeros
            String frnStr = String.format(fFormatPrecisionStr, frn); // format decimal to 5 place including zeros

            lineParts.add("F" + frnStr);

        } // if appendFvalueToLine -- add F to end of line

        // accumulate total distance traveled X,Y,Z
        if (distanceTraveled >= 0)
        {
            this.totalToolPathDistance += distanceTraveled;
        }

        // accumulate total rotation
        if (degreesRotated >= 0)
        {
            this.totalDegreesRotated += degreesRotated;
        }

        // !! copy newCoordinate to old...  but don't set values that are not set....  don't transfer I J values
        lastCoordinate = new GCodeCoordinate(); // clear the old coordinate
        lastCoordinate.copyUnsetValuesfromPrevousPoint(endCoordinate); // copy the end point from the new coordinate

        // stats on rotary axis max/min
        if (lastCoordinate.isAset() == true)
        {
            if (rotaryMinMaxFound == false)
            {
                rotaryMax = lastCoordinate.getA();
                rotaryMin = lastCoordinate.getA();
                rotaryMinMaxFound = true; // flag we have found one value
            } // first rotary data
            else
            {
                // check max
                if (lastCoordinate.getA() > rotaryMax)
                {
                    rotaryMax = lastCoordinate.getA();
                }

                // check min
                if (lastCoordinate.getA() < rotaryMin)
                {
                    rotaryMin = lastCoordinate.getA();
                }

            } // check stats, after first A move

        } // A max/min stats

        return moveContainsRotaryChangeAndNotG00;

    } // processMovingGCodesAndImplicitMoves

    // returns true if the line is a movement command, and also contains information if the line is implicit or explicit (and if so the G command)
    private GCodeMovementResult checkForLineMovementCommands(ArrayList<String> lineParts)
    {
        String[] movementKeys =
        {
            "G0", "G00", "G01", "G1", "G02", "G2", "G03", "G3"
        }; // must match exactly
        String[] movementKeysImplicitPrefix =
        {
            "X", "Y", "Z", "A"
        }; // starts with these letters

        // check all parts since some lines can start with "Lnnnn" for line numbers
        for (int i = 0; i < lineParts.size(); i++)
        {
            // exact movement command checks -- must match exactly, otherwise G3 would match G30 which is not a movement (example)
            for (String key : movementKeys)
            {
                if (lineParts.get(i).equalsIgnoreCase(key))
                {
                    // this is a movement line - with a specified G code, send results back
                    return new GCodeMovementResult(true, false, lineParts.get(i));

                }
            } // for each key - exact match

            // implicit prefix checks
            for (String key : movementKeysImplicitPrefix)
            {
                if (lineParts.get(i).startsWith(key))
                {
                    // this is a movement line - however it is an implicit move
                    return new GCodeMovementResult(true, true, "");
                }
            } // for each key -- prefix match (implicit line match)

        } // for each line Part

        // if we reach here it is not a movement command line
        return new GCodeMovementResult(false, false, "");

    } //isLineMovementCommand

    // add G94 to the end of program // Every G code file must end in a M2 or M30 or be wrapped with the percent sign %.
    // returns true if the start percentage sign was found on this line (signals that the header comments can be added)
    private boolean processStartStopOfProgramAddG94ToEnd(ArrayList<String> lineParts) throws GCodeException
    {
        //endOfProgramFound = false;
        //startProgramPercentSignFound = false;

        boolean wasStartPercentSignFoundOnThisLine = false;

        for (int i = 0; i < lineParts.size(); i++)
        {
            // ------  '%' used to wrap G-code detection ------
            if (lineParts.get(i).startsWith("%"))
            {
                // is this the first percentage file found?
                if (startProgramPercentSignFound == false)
                {
                    wasStartPercentSignFoundOnThisLine = true;

                    startProgramPercentSignFound = true;

                    publish("- Start of program found (%) on line" + (this.linesProcessed + 1));
                } // this is the first time detecting an ending "%"
                else if (endOfProgramFound == false)
                {
                    // add a G94 command to the line, so it appears on a separate line before the %
                    lineParts.set(i, "\nG94 (Added to revert back to standard G94 feed rate mode)\nF" + this.currentFeedRate + " (last feed rate used)\n" + lineParts.get(i));

                    publish("- End of program found (%) on line" + (this.linesProcessed + 1));

                    endOfProgramFound = true;
                } // if this is the first "end program" found // the end of the program has been found via %

            } // this is a G-code entry

            // ----  M2 or M30 used to detect end ------
            // warning -- starts with M2 could have problems if there were a M22 command but I don't think there are any such commands
            if (lineParts.get(i).startsWith("M2") || lineParts.get(i).startsWith("M30"))
            {
                if (endOfProgramFound == false)
                {
                    // add a G94 command to the line, so it appears on a separate line before the %
                    lineParts.set(i, "\nG94 (Added to revert back to standard G94 feed rate mode)\nF" + this.currentFeedRate + " (last feed rate used)\n" + lineParts.get(i));

                    publish("- End of program found (M2 and/or M30) on line" + (this.linesProcessed + 1));

                    endOfProgramFound = true;
                } // if end not added yet

            } // if -- M2 of M30

        }  // for each element

        return wasStartPercentSignFoundOnThisLine;
    } //processStartStopOfProgramAddG94ToEnd

    // ------------  methods to publish data or to signal completion ---------
    @Override
    protected void process(List<String> chunks)
    {
        for (String str : chunks)
        {

            try
            {
                messageTextArea.append(str + "\n");
            } catch (Exception ignore)
            {
            }

        } // or each chunk / string

    } // process (messages to display during execution)

    @Override
    protected void done()
    {
        try
        {
            long endTime = System.nanoTime();
            double durationSeconds = (endTime - startTime) / 1000000000.0f;  //divide by 1000000 to get milliseconds. or 1e9 for seconds

            long totalG0123Lines = totalG00lines + totalG01lines + totalG02lines + totalG03lines;
            double fractionLinesWithAM = numberLinesWithRotaryMoves * 1.0 / (totalG0123Lines);

            messageTextArea.append("--------------------------\n");
            messageTextArea.append("Number of G00 lines: " + totalG00lines + "\n");
            messageTextArea.append("Number of G01 lines: " + totalG01lines + "\n");
            messageTextArea.append("Number of G02 lines: " + totalG02lines + "\n");
            messageTextArea.append("Number of G03 lines: " + totalG03lines + "\n");
            messageTextArea.append("Total G00+G01+G02+G03 lines: " + (totalG0123Lines) + "\n");
            messageTextArea.append("Number lines with rotary moves: " + numberLinesWithRotaryMoves + "  (" + (int)(fractionLinesWithAM*100.0) + "%)\n");
            messageTextArea.append("Rotary (A-axis) Min/Max rotations: " + (rotaryMinMaxFound ? (rotaryMin + ", " + rotaryMax) : "No rotary axis moves found") + "\n");
            messageTextArea.append("Total toolpath distance (including rotary moves): " + String.format("%.4f", totalToolPathDistance) + "\n");
            messageTextArea.append("Total degrees of rotation for A-axis: " + String.format("%.4f", totalDegreesRotated) + "\n");
            messageTextArea.append("--------------------------\n");
            messageTextArea.append("Completed! [" + String.format("%1$,.2f", durationSeconds) + " seconds]\n");

            // --- a couple helpful hints: -- regaurding selection of conversion mode
            if (totalG0123Lines > 50) // if a decent sized file chunk has been processed (this helps filter out error cases)
            {

                if (this.conversionMode == RapidRotary_GUI.ConversionMode.WRAP_ALL
                        && fractionLinesWithAM < 0.15)
                {
                    // show alert on main thread
                    SwingUtilities.invokeLater(new Runnable()
                    {
                        public void run()
                        {
                            JOptionPane.showMessageDialog(callbackObject,
                                    "Only a small percentage of the lines in the input file had rotary moves.  It may be more efficient to use the 'Wrap each rotary move in G93' option instead of converting the entire file.",
                                    "Warning- Conversion Method",
                                    JOptionPane.WARNING_MESSAGE);
                        }
                    }); // alert on main thread
                } // if they may not want wrap all

                if (this.conversionMode == RapidRotary_GUI.ConversionMode.WRAP_EACH_A_MOVE
                        && fractionLinesWithAM > 0.25)
                {
                    // show alert on main thread
                    SwingUtilities.invokeLater(new Runnable()
                    {
                        public void run()
                        {
                            JOptionPane.showMessageDialog(callbackObject,
                                    "A sizeable percentage of the lines in the input file had rotary moves.  It may be more efficient to select the 'Use G93 mode for the entire file' option instead of wrapping each rotary move.",
                                    "Warning- Conversion Method",
                                    JOptionPane.WARNING_MESSAGE);
                        }
                    }); // alert on main thread
                } // if they may want to wrap all

            } // hints on conversion mode -- if file size is big enough

            callbackObject.conversionSwingWorkerFinishedCallback(); // call back to orginal to signal completion

        } catch (Exception ignore)
        {
        }

    } // done

} // GlobalFileConverterSwingWorker class
