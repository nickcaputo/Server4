package app_threads;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import main_threads.Server4;
import tools.DatabaseTools;
import tools.Folders;
import tools.InterAppComm;
import tools.TimeTools;
import tools.Write;

/**
 * 
 * @author Nicholas Caputo, npocaputo@GMail.com, (847) 630 7370
 * 
 *         TODO instead of showing the item number when clocking in or out on a
 *         work order, we have to show the number that the item was stored as.
 *         So instead of 130817, we have to show "Item #1"
 *
 */
public class Timecard {
	private String rootPath;
	private String rmAppServerPath;
	private String entry;

	private long counter;
	private Socket socket;
	private short stepNumber = 0;

	private static final String APP_NAME = "Timecard";
	private static final short MS_BEFORE_DELETING_FOLDER = 100;

	private static final String SEPARATOR = "^";
	private static final String PIPE_DELIMITER = "\\|";
	private static final String LINE_BREAK_SEPARATOR = "§¶§";
	private static final String COMPATIBLE_L_B_SEP = "Â§Â¶Â§";
	private static final String VFP9_LINE_BREAK = "\" + chr(13) + \"";
	private static final String WO = "wo";
	private static final String LOGIN = "login";
	private static final String GET_STOCKNUMBER = "query_getStockno";
	private static final String CLOCK_IN = "clockIn";
	private static final String AUTO_CLOCK_OUT = "autoClockOut";
	private static final String QUERY_CHECK_CLOCKED_OUT = "query_checkClock";
	private static final String WO_CLOCK_IN = WO + '_' + "clockIn";
	private static final String WO_AUTO_CLOCK_OUT = WO + '_' + "autoClockOut";
	private static final String QUERY_WO_CHECK_CLOCKED_OUT = "query" + '_' + WO + '_' + "checkClock";
	private static final String QUERY_GET_TRANKEY = "query_getTrankey";
	private static final String WRITE_SOLUTION = "writeSln";
	private static final String QUERY_FILE_EXT = ".txt";
	private static final String RESULT_FILE_EXT = ".csv";

	/*
	 * This is set in the generateShellCommand method when the command to create
	 * the file is written, and it is used in the seeInsideCSVFile method where
	 * it is used to tell the system which file to look at.
	 */
	private String nameOfFile = "";

	// for debugging purposes
	private long startTime;

	/**
	 * starts the main worker thread for Inventory, and keeps track of the
	 * command that it is given, and the counter number in the main Server class
	 * 
	 * @param entry
	 * @param counter
	 */
	public Timecard(String entry, long counter) {
		startTime = System.nanoTime();

		socket = Server4.getSocket(counter);
		this.entry = entry;

		this.counter = counter;
		rmAppServerPath = Server4.getRMAppServerPath();
		rootPath = rmAppServerPath.substring(0, Server4.getRMAppServerPath().lastIndexOf('\\') + 1);

		connection.start();
		log("Timecard service has started.");
	}

	Thread connection = new Thread() {
		public void run() {

			try {

				/*
				 * creates the folder to be used throughout this cycle for the
				 * application
				 */
				File folder = new File("timecard" + counter);
				boolean createdFolder = folder.mkdir();

				if (!createdFolder) {
					/*
					 * this is reached if the folder already exists, so we try
					 * deleting the folder, then recreating it
					 */
					log("This folder already exists, deleting the contents and reusing the folder.");
					Folders.deleteFolder(folder, false);
				}

				log("Using the temporary folder : " + folder.getName());

				/*
				 * starts the main responsibilities for the server application
				 */
				startResponsibilities(folder);

				/*
				 * We've sent back the results by now, but the VFP9 program may
				 * still be using the file for a bit. Wait to be sure it stopped
				 * using it.
				 */

				Folders.deleteFolder(folder, true);

				socket.close();

			} catch (IOException err) {
				err.printStackTrace();
			} finally {

				long futureTime = System.nanoTime();

				long nanos = futureTime - startTime;
				long millis = nanos / 1000000;
				log("This operation took " + nanos + " nanoseconds (" + millis + " ms).");

				Server4.removeThread("Timecard", counter);
			}
		}
	};

	/*
	 * 
	 * 
	 * 
	 * 
	 * 
	 * ****************BEGIN ESSENTIAL METHODS FOR ALL COMMANDS**************
	 * 
	 * 
	 * 
	 * 
	 * 
	 */

	/**
	 * Performs all of the functions required for someone to log into the
	 * application from their phone and all other major functions, such as
	 * checking to make sure the user is not still clocked in on a previous
	 * session and clocking the user out if they are.
	 * 
	 * @param authenticatedData
	 * @param username
	 * @param folder
	 * @return the string with the status on it
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws ParseException
	 * @throws SQLException
	 */
	private void startResponsibilities(File folder) throws IOException {
		log("Starting the main responsibilities method, deciding what to do with this request.");
		PrintWriter outToClient = new PrintWriter(socket.getOutputStream());

		String[] data = entry.split("\\" + SEPARATOR);
		String command = data[0];
		String username = data[1];
		String wonumber = "";
		String itemNumber = "";
		String solution = "";
		String clockInInfo = "";

		/*
		 * count how many separators are in the entry, and enforce that there
		 * must be that many + 1 items in the data array
		 */

		if (command.equals(WO_CLOCK_IN)) {
			wonumber = data[2];
			itemNumber = data[3];
		} else if (command.equals(WRITE_SOLUTION)) {
			wonumber = data[2];
			itemNumber = data[3];
			solution = data[4];
		} else if (command.equals(AUTO_CLOCK_OUT)) {
			clockInInfo = data[2];
		}

		String[] authenticatedData = {};

		try {
			authenticatedData = authentication(username);
		} catch (SQLException authenticationError) {
			authenticationError.printStackTrace();
		}

		/*
		 * tests the command string to determine what to do with the data
		 */

		String resultToReturn = useData(folder, authenticatedData, command, username, clockInInfo, wonumber, itemNumber,
				solution);

		log("The results being returned are: " + resultToReturn);

		outToClient.write(resultToReturn);

		outToClient.close();

	}

	/**
	 * Uses the received and authenticated data from the previous method and
	 * runs the desired commands for it, returning the result from the database.
	 * 
	 * @param folder,
	 *            the temporary folder where all temp files are stored
	 * @param authenticatedData,
	 *            the array of authenticated data from the database
	 * @param command,
	 *            the command from the client to perform
	 * @param username,
	 *            the username for the client
	 * @param wonumber,
	 *            the work order number, if there is one
	 * @param itemNumber,
	 *            the item number, if there is one
	 * @param solution,
	 *            the solution if working on a work order
	 * @return result from the database
	 */
	private String useData(File folder, String[] authenticatedData, String command, String username, String clockInInfo,
			String wonumber, String itemNumber, String solution) {
		if (authenticatedData.length >= 6) {
			StringBuilder resultBuilder = new StringBuilder();

			String techId = authenticatedData[0];
			String userId = authenticatedData[1];
			String locId = authenticatedData[2];
			String dbfPath = authenticatedData[3];
			boolean uploadPictures = Boolean.parseBoolean(authenticatedData[4]);
			int hourlyRate = Integer.parseInt(authenticatedData[5]);
			int maxClock = Integer.parseInt(authenticatedData[6]);
			String realName = authenticatedData[7];

			log("Successfully authenticated user " + techId);

			try {
				switch (command) {
				case LOGIN:
					log("*****************This is a request to log into the app.");
					String mainClockStatus = seeIfStillClockedIn(folder, authenticatedData, QUERY_CHECK_CLOCKED_OUT);
					String workOrderClock = seeIfStillClockedIn(folder, authenticatedData, QUERY_WO_CHECK_CLOCKED_OUT);
					boolean mainClock_clockedIn = !mainClockStatus.contains("clocked out");
					boolean workOrder_clockedIn = !workOrderClock.contains("clocked out");

					resultBuilder.append("techId:").append(techId).append(SEPARATOR);
					resultBuilder.append("userId:").append(userId).append(SEPARATOR);
					resultBuilder.append("locId:").append(locId).append(SEPARATOR);
					resultBuilder.append("dbfPath:").append(dbfPath).append(SEPARATOR);
					resultBuilder.append("uploadPictures:").append(uploadPictures).append(SEPARATOR);
					resultBuilder.append("hourlyRate:").append(hourlyRate).append(SEPARATOR);
					resultBuilder.append("maxClock:").append(maxClock).append(SEPARATOR);
					resultBuilder.append("counter:").append(counter).append(SEPARATOR);
					resultBuilder.append("realName:").append(realName).append(SEPARATOR);

					resultBuilder.append("mainClock_clockedIn:").append(mainClock_clockedIn).append(SEPARATOR);

					if (mainClockStatus.contains(",")) {
						String[] dateTime = parseResult(mainClockStatus);

						resultBuilder.append("mainClock_clockInDate:").append(dateTime[0]).append(SEPARATOR);
						resultBuilder.append("mainClock_clockInTime:").append(dateTime[1]).append(SEPARATOR);
					} else {
						resultBuilder.append("mainClockStatus:").append(mainClockStatus).append(SEPARATOR);
					}

					resultBuilder.append("workOrder_clockedIn:").append(workOrder_clockedIn).append(SEPARATOR);

					if (workOrderClock.contains(",")) {
						String[] results = parseResult(workOrderClock);

						combineParseWorkOrder(results, resultBuilder);
					}

					break;

				case CLOCK_IN:
					log("*********************This is a request to clock in on the main clock.");

					String clockInResult = clockIn(username, folder, authenticatedData);
					String[] dateTime = TimeTools.getArrayDateTime();

					resultBuilder.append("mainClockStatus:").append(clockInResult).append(SEPARATOR);
					resultBuilder.append("mainClock_clockInDate:").append(dateTime[0]).append(SEPARATOR);
					resultBuilder.append("mainClock_clockInTime:").append(dateTime[1]).append(SEPARATOR);
					resultBuilder.append("mainClock_clockedIn:true");
					break;

				case AUTO_CLOCK_OUT:
					log("***********************This is a request to clock out on the main clock.");

					String clockOutResult = clockOut(clockInInfo, folder, authenticatedData);
					// hired

					resultBuilder.append(clockOutResult).append(SEPARATOR);
					resultBuilder.append("mainClockStatus:User has been clocked out.").append(SEPARATOR);
					resultBuilder.append("mainClock_clockedIn:false");
					break;

				case WO_CLOCK_IN:
					log("*************************This is a request to clock in on a work order.");

					workOrderClockIn(username, wonumber, itemNumber, folder, authenticatedData);
					String workOrderInformation = seeIfStillClockedIn(folder, authenticatedData,
							QUERY_WO_CHECK_CLOCKED_OUT);

					resultBuilder.append("Clocked in on the work order.").append(SEPARATOR);

					String[] results = parseResult(workOrderInformation);

					if (results.length >= 6) {
						resultBuilder.append("workOrder_clockedIn:true").append(SEPARATOR);

						combineParseWorkOrder(results, resultBuilder);
					} else {
						resultBuilder.append("workOrder_clockedIn:false").append(SEPARATOR);
						resultBuilder.append("Error in clocking in on a work order.");
					}
					break;

				case WO_AUTO_CLOCK_OUT:
					log("***************************This is a request to clock out on a work order.");

					workOrderClockOut(username, folder, authenticatedData);

					resultBuilder.append("workOrder_clockedIn:false");
					break;

				case WRITE_SOLUTION:
					log("****************************This is a request to write the solution for the work order.");

					String solutionResult = writeSolution(folder, wonumber, itemNumber, solution, authenticatedData);
					resultBuilder.append(solutionResult);
					break;
				}
			} catch (InterruptedException | IOException | ParseException | SQLException err) {
				err.printStackTrace();
			}

			String resultToSend = resultBuilder.toString();
			return resultToSend;
		} else {
			log("ERROR: There was not the proper amount of authenticated data (must be at least 6 items).");

			String errorMessage = "There was an error authenticating the user, please try it again. ";
			return errorMessage;
		}
	}

	/**
	 * When checking the work order clock to see if someone is clocked in, we
	 * get the date, time, work order number, item number, and stock number
	 * back. This method will format those results to be understood by the
	 * phone. If it is for any other type of clocking in, it will return the
	 * string array of the date and time of last clock in.
	 * 
	 * @param result,
	 *            the comma separated string of values
	 * @return the array of items
	 */
	private static String[] parseResult(String result) {
		String[] split = result.split(",");

		if (split.length > 6) {
			String date = split[0];
			String time = split[1];
			String itemnumber = split[2];
			String stocknumber = split[3];
			String wonumber = split[4];
			String englishItemNum = split[5];

			return new String[] { date, time, itemnumber, stocknumber, wonumber, englishItemNum };
		} else if (split.length == 2) {
			String date = split[0];
			String time = split[1];

			return new String[] { date, time };
		} else {
			return split;
		}
	}

	private void combineParseWorkOrder(String[] results, StringBuilder resultBuilder) {
		resultBuilder.append("workOrder_clockInDate:").append(results[0]).append(SEPARATOR);
		resultBuilder.append("workOrder_clockInTime:").append(results[1]).append(SEPARATOR);
		resultBuilder.append("workOrder_itemnumber:").append(results[2]).append(SEPARATOR);
		resultBuilder.append("workOrder_stocknumber:").append(results[3]).append(SEPARATOR);
		resultBuilder.append("workOrder_wonumber:").append(results[4]).append(SEPARATOR);
		resultBuilder.append("workOrder_englishItemNum:").append(results[5]).append(SEPARATOR);
	}

	/**
	 * Authenticates the user with the RMAPPServer database
	 * 
	 * @param username
	 * @return the string containing all information about the user
	 * @throws SQLException
	 */
	private String[] authentication(String username) throws SQLException {
		log("Starting the user authentication method now.");

		/*
		 * these are the variables which are stored in the database
		 */
		String techId = "";
		String userId = "";
		String locId = "";
		String dbfPath = "";
		boolean uploadPictures = false;
		int hourlyRate = -1;
		int maxClock = -1;
		String realName = "";

		ResultSet userInformation = DatabaseTools.authenticateTimecard(rmAppServerPath, username);

		while (userInformation.next()) {
			techId = userInformation.getString("Tech_ID");
			userId = userInformation.getString("User_ID");
			locId = userInformation.getString("LocId");
			dbfPath = userInformation.getString("FilePath");
			uploadPictures = userInformation.getBoolean("UploadPics");
			hourlyRate = userInformation.getInt("hrpay");
			maxClock = userInformation.getInt("MaxClock");
			realName = userInformation.getString("User_Real_Name");
		}

		/*
		 * this is the string sent back from the method, containing all user
		 * information
		 */
		String[] sendBackToDevice = { techId, userId, locId, dbfPath, "" + uploadPictures, "" + hourlyRate,
				"" + maxClock, realName };

		return sendBackToDevice;
	}

	/*
	 * 
	 * 
	 * 
	 * 
	 * 
	 * **************BEGIN MAIN CLOCK IN/CLOCK OUT METHODS****************
	 * 
	 * 
	 * 
	 * 
	 * 
	 */

	/**
	 * clocks the user in with the current date and time
	 * 
	 * @param username
	 * @param folder
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	private String clockIn(String username, File folder, String[] authenticatedData)
			throws InterruptedException, IOException, SQLException {
		log("Starting the main clock in method.");

		String shellCommand = generateShellCommand(folder, CLOCK_IN, authenticatedData, null);

		/*
		 * executes the generated command in the system shell
		 */
		shellExecute(shellCommand);

		return "clocked in";
	}

	/**
	 * Checks to see if the user is still clocked in. Works for either a work
	 * order or the main clock.
	 * 
	 * @param folder
	 * @param authenticatedData
	 * @param command
	 * @return info if the user is clocked in
	 * @throws InterruptedException
	 * @throws IOException
	 */
	private String seeIfStillClockedIn(File folder, String[] authenticatedData, String command)
			throws InterruptedException, IOException {

		log("Starting the seeIfStillClockedIn method with command " + command);

		if (command != null) {
			String commandToExecute = generateShellCommand(folder, command, authenticatedData, null);

			// executes the generated command in the system shell
			shellExecute(commandToExecute);

			// now we check to see if the user is already clocked in
			String clockInCheck = seeInsideCSVFile(folder);

			if (clockInCheck != null) {
				return clockInCheck;
			} else {
				return "User was clocked out.";
			}

		} else {
			return "command was null";
		}
	}

	/**
	 * begins all the processes required to clock the user out automatically.
	 * 
	 * @param folder
	 * @param infoFromLastClockIn
	 * @param authenticatedData
	 * @throws IOException
	 * @throws ParseException
	 */
	private String clockOut(String clockInInfo, File folder, String[] authenticatedData)
			throws InterruptedException, IOException, ParseException {
		log("Starting the main clock out method.");
		String clockOutStatus = "";

		/*
		 * The parameter clockInInfo passed from the phone has the last time the
		 * user used the main clock
		 * 
		 * if the infoFromLastClockIn contains a comma, that means that there is
		 * a clock in time that is still active
		 */
		if (clockInInfo.contains(",")) {
			/*
			 * creates a String object for the date and time at last clock in
			 */
			String timeFormatted = clockInInfo.replace(',', ' ').replaceAll("\"", "");
			log("There is still an active session from " + timeFormatted + ", clocking the user out.");

			/*
			 * generates a String object for today's date and time
			 */
			DateFormat format = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US);
			String today = format.format(new Date());

			int maxClock = Integer.parseInt(authenticatedData[6]);

			/*
			 * Runs the getClockOutTime method
			 */
			String[] clockOutData = getClockOutTime(timeFormatted, today, maxClock);

			String shellCommand = generateShellCommand(folder, AUTO_CLOCK_OUT, authenticatedData, clockOutData);

			/*
			 * executes the command in the system shell
			 */
			shellExecute(shellCommand);

			clockOutStatus = "User has been clocked out.";
		} else {
			clockOutStatus = "The user was already clocked out.";
		}

		log(clockOutStatus + "  Returning this data now.");
		return clockOutStatus;
	}

	/*
	 * 
	 * 
	 * 
	 * 
	 * 
	 * *********BEGIN WORK ORDER SPECIFIC CLOCK ACTION METHODS*************
	 * 
	 * 
	 * 
	 * 
	 * 
	 */

	/**
	 * Is used when clocking in on a work order, and delegates responsibilities
	 * of creating the file with the SQL Statement, creating the shell command,
	 * reading the file created by the command, and repeating the process for
	 * marking down in the official record that a clock in occurred.
	 * 
	 * @param username
	 * @param wonumber
	 * @param itemnumber
	 * @param folder
	 * @param authenticatedData
	 * @return
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SQLException
	 */
	private void workOrderClockIn(String username, String wonumber, String itemnumber, File folder,
			String[] authenticatedData) throws InterruptedException, IOException, SQLException {
		log("Starting the work order clock in method.");

		String[] workOrderItemNumbers = { wonumber, itemnumber };

		/*
		 * Gets stocknumber from Wotran
		 * 
		 * 
		 * TODO implement an SQL Statement which reads
		 * 
		 * INSERT INTO Wotime(Wonumber, Tech_id, Itemno, Stockno, Datein,
		 * Timein, Trankey, User_id) VALUES ('WO20886', 'STJEANM',
		 * 
		 * '103817', (SELECT Stockno FROM Wotran WHERE Wonumber='wo20886' AND
		 * Itemno='103817'), DATE(), TIME(), sys(2015), '99999') as an example
		 * 
		 * However, this gives Error 1300, "function name is missing )" Speak
		 * with Henry, John or David about this, the syntax is correct
		 */
		String shellCommand = generateShellCommand(folder, GET_STOCKNUMBER, authenticatedData, workOrderItemNumbers);

		shellExecute(shellCommand);

		String stocknumber = seeInsideCSVFile(folder);
		stocknumber = stocknumber.replaceAll("\"", "");

		/*
		 * Use stocknumber to clock in on the Wotime table
		 */

		if (stocknumber != null) {
			log("The stock number for this item is " + stocknumber + ", using it in the clock in command.");
			String[] completeWOItemNumbers = Arrays.copyOf(workOrderItemNumbers, workOrderItemNumbers.length + 1);
			completeWOItemNumbers[2] = stocknumber;

			shellCommand = generateShellCommand(folder, WO_CLOCK_IN, authenticatedData, completeWOItemNumbers);

			shellExecute(shellCommand);
		}

		log("Clocked in on the work order.");

	}

	private void workOrderClockOut(String username, File folder, String[] authenticatedData)
			throws InterruptedException, IOException, ParseException, SQLException {
		log("Starting the work order clock out method.");

		String workOrderClockInStatus = seeIfStillClockedIn(folder, authenticatedData, QUERY_WO_CHECK_CLOCKED_OUT);

		if (workOrderClockInStatus.contains(",")) {
			log("User still clocked in at " + workOrderClockInStatus);

			String timeFormatted = workOrderClockInStatus.replace(',', ' ').replaceAll("\"", "");
			log("There is an active work order session from " + timeFormatted + ", clocking the user out.");

			/*
			 * generates a String object for today's date and time
			 */
			DateFormat format = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US);
			String today = format.format(new Date());

			int maxClock = Integer.parseInt(authenticatedData[6]);

			/*
			 * Runs the getClockOutTime method
			 */
			String[] clockOutData = getClockOutTime(timeFormatted, today, maxClock);

			String autoClockOutCommand = generateShellCommand(folder, WO_AUTO_CLOCK_OUT, authenticatedData,
					clockOutData);

			shellExecute(autoClockOutCommand);
		}

		log("Clocked out of the work order.");
	}

	/**
	 * Writes a solution on the work order in the solution.dbf table of the VFP
	 * 9 database
	 * 
	 * @param folder
	 * @param wonumber
	 * @param itemnumber
	 * @param solution
	 * @param authenticatedData
	 * @return a string showing whether it was successful!
	 * @throws InterruptedException
	 * @throws IOException
	 */
	private String writeSolution(File folder, String wonumber, String itemnumber, String solution,
			String[] authenticatedData) throws InterruptedException, IOException {
		log("writeSolution method starting now with solution " + solution);
		String messageToReturn = "Solution not written.  Error code 1.";
		String[] allInformation = { wonumber, itemnumber, solution };

		String shellCommand = generateShellCommand(folder, WRITE_SOLUTION, authenticatedData, allInformation);

		if (!shellCommand.equals("do not execute")) {
			log("Updating solution with " + solution);

			shellExecute(shellCommand);

			messageToReturn = "Solution written successfully!";
		}

		return messageToReturn;
	}

	/*
	 * 
	 * 
	 * 
	 * 
	 * 
	 * *******BEGIN METHODS TO HANDLE SHELL COMMANDS**************
	 * 
	 * 
	 * 
	 * 
	 * 
	 */

	/**
	 * Generates the shell command to run in FoxPro, depending on the type of
	 * operation you have to do. The clockOutData may be a null String array,
	 * but only if the user is not calling the clockOut or autoClockOut
	 * functions.
	 * 
	 * @param type
	 * @param authenticatedData
	 * @return the Shell command in String format
	 */
	private String generateShellCommand(File folder, String type, String[] authenticatedData, String[] extras) {
		log("Starting the generateShellCommand method with type " + type);

		/*
		 * generates the String detailing the path to the export executable, and
		 * the path to the table in the file system
		 */
		String dbfPath = authenticatedData[3];
		char appendedChar = dbfPath.charAt(dbfPath.length() - 1);
		String pathToExportExe = "\"" + rootPath + "export\"";

		String baseStringForPaths = dbfPath + "\\ctrldbf" + appendedChar;
		String pathToWoclock = baseStringForPaths + "\\woclock";
		String pathToWotime = baseStringForPaths + "\\wotime";
		String pathToWotran = baseStringForPaths + "\\wotran";
		String pathToSolution = baseStringForPaths + "\\solution";

		/*
		 * generates the text file that contains the SQL query (this text file
		 * is read by the export executable when it runs it on the table) and
		 * returns the location of it in the file system
		 */
		String pathToQuery = generateQueryTextFile(folder, type, authenticatedData, extras);

		/*
		 * if there is a problem creating the text file containing the SQL
		 * statement, the string returned says "do not execute". This is the
		 * check to prevent errors from happening in the application.
		 */
		if (!pathToQuery.equals("do not execute")) {
			/*
			 * determines whether the SQL statement is a query and updates the
			 * third parameter accordingly
			 */
			String thirdParameter;
			if (type.contains("query")) {
				thirdParameter = '\"' + rootPath + folder.getPath() + "\\" + type + "\"";
				nameOfFile = type;
			} else {
				thirdParameter = "\"execute\"";
			}

			/*
			 * This is where the shell command is generated and returned
			 */
			String commandToExecute;

			if (type.equals(GET_STOCKNUMBER) || type.equals(QUERY_GET_TRANKEY)) {
				commandToExecute = pathToExportExe + ' ' + '\"' + pathToWotran + '\"' + ' ' + pathToQuery + ' '
						+ thirdParameter;
			} else if (type.equals(WRITE_SOLUTION)) {
				commandToExecute = pathToExportExe + ' ' + '\"' + pathToSolution + ';' + pathToWotran + '\"' + ' '
						+ pathToQuery + ' ' + thirdParameter;
			} else if (type.contains(WO)) {
				commandToExecute = pathToExportExe + ' ' + '\"' + pathToWotime + ';' + pathToWotran + '\"' + ' '
						+ pathToQuery + ' ' + thirdParameter;
			} else {
				commandToExecute = pathToExportExe + ' ' + '\"' + pathToWoclock + '\"' + ' ' + pathToQuery + ' '
						+ thirdParameter;
			}

			return commandToExecute;
		} else {
			/*
			 * returns the command not to execute
			 */
			log("Not executing the command.");
			return pathToQuery; // pathToQuery == "do not execute";
		}
	}

	/**
	 * Executes a command on the shell, typically used with the export.exe in
	 * the application root directory. It also waits for changes to be made in
	 * the database (average of 100ms) for each execution.
	 * 
	 * @param command
	 * @throws IOException
	 */
	private void shellExecute(String command) throws InterruptedException, IOException {
		log("Starting the shellExecute method, now executing command " + command);
		Runtime.getRuntime().exec(command);

		InterAppComm.sleep(MS_BEFORE_DELETING_FOLDER);
	}

	/*
	 * 
	 * 
	 * 
	 * 
	 * ****BEGIN METHODS TO HANDLE CREATING TEMPORARY SQL QUERY TEXT FILES*****
	 *
	 *
	 *
	 *
	 *
	 */

	/**
	 * generates the text file that contains the query to be used in updating
	 * the tables.
	 * 
	 * @param authenticatedData
	 * @return the path to the query in a String
	 */
	private String generateQueryTextFile(File folder, String type, String[] authenticatedData, String[] extras) {
		log("The generateQueryTextFile method has started.");
		String techId = authenticatedData[0];
		String userId = authenticatedData[1];

		/*
		 * determines which file to make
		 */
		String fileName = "no file yet";
		boolean fileWasMade = false;
		fileName = folder.getPath() + "/" + type + QUERY_FILE_EXT;

		switch (type) {
		case GET_STOCKNUMBER:
			fileWasMade = makeWOGetStocknumberFile(folder, techId, extras);
			break;
		case CLOCK_IN:
			fileWasMade = makeCIFile(folder, techId, userId);
			break;
		case AUTO_CLOCK_OUT:
			fileWasMade = makeAutoCOFile(folder, techId, extras);
			break;
		case QUERY_CHECK_CLOCKED_OUT:
			fileWasMade = makeNCOFile(folder, techId);
			break;
		case WO_CLOCK_IN:
			fileWasMade = makeWorkOrderCIFile(folder, techId, userId, extras);
			break;
		case WO_AUTO_CLOCK_OUT:
			fileWasMade = makeAutoWorkOrderCOFile(folder, techId, extras);
			break;
		case QUERY_WO_CHECK_CLOCKED_OUT:
			fileWasMade = makeWorkOrderNCOFile(folder, techId);
			break;
		case QUERY_GET_TRANKEY:
			fileWasMade = makeGetTrankeyFile(folder, extras);
			break;
		case WRITE_SOLUTION:
			fileWasMade = makeWriteSolutionFile(folder, extras);
			break;
		}

		/*
		 * either sends back the path to the Query text file or sends back a
		 * command to not execute the result
		 */
		String pathToQuery;

		if (fileWasMade) {
			pathToQuery = "\"*" + rootPath + fileName + "\"";
			log("The file was successfully created and saved as " + fileName + '.');
		} else {
			log("The command could not be executed, the file was not created.");
			pathToQuery = "do not execute"; // or throw an exception here
		}

		return pathToQuery;
	}

	/**
	 * this method creates the text file containing the SQL statement which
	 * checks to see if the user has not clocked out from a previous session
	 * 
	 * @param techId
	 * @return whether the file was successfully created
	 */
	private boolean makeNCOFile(File folder, String techId) {
		log("MakeNCOFile (see if still clocked in on main clock) method has started.");

		String checkClockInSql = "SELECT Date_in, Clock_in FROM woclock WHERE Tech_id='" + techId
				+ "' AND EMPTY(Date_out) AND EMPTY(Clock_out)";

		/*
		 * creates the file in the system and returns the result if it was
		 * created.
		 */
		boolean successfullyMadeFile = createFileInSystem(folder, QUERY_CHECK_CLOCKED_OUT + QUERY_FILE_EXT,
				checkClockInSql);

		return successfullyMadeFile;
	}

	/**
	 * Makes the file to check and see if a work order is still clocked in.
	 * Usually asked for when clocking out of that work order.
	 * 
	 * @param folder
	 * @param techId
	 * @return
	 */
	private boolean makeWorkOrderNCOFile(File folder, String techId) {
		log("makeWorkOrderNCOFile (check to see if still clocked in on work order) method has started.");

		String checkClockInSql = "SELECT wotime.Datein, wotime.Timein, wotime.Itemno, wotime.Stockno, "
				+ "wotime.Wonumber, LEFT(wotran.Descrip, 3) AS itemnumber FROM wotime LEFT JOIN "
				+ "wotran ON wotime.itemno=wotran.itemno WHERE Tech_id='" + techId
				+ "' AND EMPTY(Dateout) AND EMPTY(Timeout)";

		/*
		 * creates the file in the system and returns the result if it was
		 * created.
		 */
		boolean successfullyMadeFile = createFileInSystem(folder, QUERY_WO_CHECK_CLOCKED_OUT + QUERY_FILE_EXT,
				checkClockInSql);

		return successfullyMadeFile;
	}

	/**
	 * makes the file containing the stocknumber from wotran. It is used to
	 * clock in on a work order.
	 * 
	 * @param folder
	 * @param techId
	 * @param workOrderItemNumbers
	 * @return
	 */
	private boolean makeWOGetStocknumberFile(File folder, String techId, String[] workOrderItemNumbers) {
		log("makeGetStocknumberFile method started.");

		String wonumber = workOrderItemNumbers[0];
		String itemnumber = workOrderItemNumbers[1];

		String getStocknumberSql = "SELECT Stockno FROM Wotran WHERE Wonumber=\"" + wonumber + "\" AND Itemno=\""
				+ itemnumber + "\"";

		boolean successfullyMadeFile = createFileInSystem(folder, GET_STOCKNUMBER + QUERY_FILE_EXT, getStocknumberSql);

		return successfullyMadeFile;
	}

	/**
	 * Makes the file which is used in clocking in to a work order. It has the
	 * proper SQL Statement which is executed upon the Wotime table.
	 * 
	 * @param folder
	 * @param techId
	 * @param userId
	 * @param completeWOItemNumbers
	 * @return
	 */
	private boolean makeWorkOrderCIFile(File folder, String techId, String userId, String[] completeWOItemNumbers) {
		log("makeWorkOrderClockInFile method started.");

		String wonumber = completeWOItemNumbers[0];
		String itemnumber = completeWOItemNumbers[1];
		String stocknumber = completeWOItemNumbers[2];

		String workOrderClockInSql = "INSERT INTO Wotime(Wonumber, Tech_id, Itemno, Stockno, Datein, Timein, Trankey, User_id) VALUES ('"
				+ wonumber + "', '" + techId + "', '" + itemnumber + "',  \'" + stocknumber
				+ "\', DATE(), TIME(), sys(2015), '" + userId + "')";

		boolean successfullyMadeFile = createFileInSystem(folder, WO_CLOCK_IN + QUERY_FILE_EXT, workOrderClockInSql);

		return successfullyMadeFile;
	}

	/**
	 * Makes the file which is used to clock out of a work order. It has the
	 * proper SQL Statement to execute upon Wotime and is safe to use even if
	 * the user is not already clocked in.
	 * 
	 * @param folder
	 * @param techId
	 * @return
	 */
	private boolean makeAutoWorkOrderCOFile(File folder, String techId, String[] clockOutData) {
		log("makeAutoWorkOrderClockOutFile method started.");

		String clockOutDate = clockOutData[0];
		String clockOutTime = clockOutData[1];
		String hoursClockedIn = truncateHours(clockOutData[2]);

		String autoClockOutSql = "UPDATE Wotime SET Dateout=ctod('" + clockOutDate + "'), Timeout='" + clockOutTime
				+ "', Hours=" + hoursClockedIn + ", Distrbuted=" + hoursClockedIn
				+ ", Not_distrb=0.0000 WHERE Tech_id='" + techId + "' AND EMPTY(Dateout) AND EMPTY(Timeout)";

		/*
		 * creates the file in the system and returns the result if it was
		 * created.
		 */
		boolean successfullyMadeFile = createFileInSystem(folder, WO_AUTO_CLOCK_OUT + QUERY_FILE_EXT, autoClockOutSql);

		return successfullyMadeFile;
	}

	/**
	 * creates the file containing the auto clock out statement, called when the
	 * user has a previous session that they are still clocked in on.
	 * 
	 * @param folder
	 * @param techId
	 * @param clockOutData
	 * @return whether the file was successfully created
	 */
	private boolean makeAutoCOFile(File folder, String techId, String[] clockOutData) {
		log("makeAutoCOFile method has started.");

		String clockOutDate = clockOutData[0];
		String clockOutTime = clockOutData[1];
		String hoursClockedIn = truncateHours(clockOutData[2]);

		String autoClockOutSql = "UPDATE Woclock SET Date_out=ctod('" + clockOutDate + "'), Clock_out='" + clockOutTime
				+ "', Hours=" + hoursClockedIn + " WHERE Tech_id='" + techId
				+ "' AND EMPTY(Date_out) AND EMPTY(Clock_out)";

		/*
		 * creates the file in the system and returns the result if it was
		 * created.
		 */
		boolean successfullyMadeFile = createFileInSystem(folder, AUTO_CLOCK_OUT + QUERY_FILE_EXT, autoClockOutSql);

		return successfullyMadeFile;
	}

	/**
	 * Makes the file which is used to clock in.
	 * 
	 * @param folder
	 * @param techId
	 * @param userId
	 * @return
	 */
	private boolean makeCIFile(File folder, String techId, String userId) {
		log("makeCIFile method started.");

		String clockInSql = "INSERT INTO Woclock(Tech_id, Date_in, Clock_in, User_id) VALUES('" + techId
				+ "', DATE(), TIME(), '" + userId + "')";

		/*
		 * creates the file and returns true if successful
		 */
		boolean successfullyMadeFile = createFileInSystem(folder, CLOCK_IN + QUERY_FILE_EXT, clockInSql);

		return successfullyMadeFile;
	}

	/**
	 * makes the getTrankey file and writes the proper SQL for it.
	 * 
	 * @param folder
	 * @param extras
	 * @return
	 */
	private boolean makeGetTrankeyFile(File folder, String[] extras) {
		log("makeGetTrankeyFile method starting now.");

		String wonumber = extras[0];
		String itemnumber = extras[1];

		String getTrankeySql = "SELECT trankey FROM wotran WHERE " + "wonumber=\"" + wonumber + "\" AND itemno=\""
				+ itemnumber + "\"";

		/*
		 * creates the file and returns true if successful
		 */
		boolean successfullyMadeFile = createFileInSystem(folder, QUERY_GET_TRANKEY + QUERY_FILE_EXT, getTrankeySql);

		return successfullyMadeFile;
	}

	private boolean makeWriteSolutionFile(File folder, String[] extras) {
		log("makeWriteSolutionFile method starting now.");

		String wonumber = extras[0];
		String itemnumber = extras[1];
		String solution = extras[2].replaceAll(LINE_BREAK_SEPARATOR, VFP9_LINE_BREAK).replaceAll(COMPATIBLE_L_B_SEP,
				VFP9_LINE_BREAK);

		String writeSolutionSql = "UPDATE solution SET txt = txt + chr(13) + chr(13) + \'SOLUTION FROM APP:\' + chr(13) + \""
				+ solution + "\" WHERE wotrankey=(SELECT trankey FROM wotran WHERE wonumber='" + wonumber
				+ "' AND itemno='" + itemnumber + "')";

		/*
		 * Creates the file and returns true if successful.
		 */
		boolean successfullyMadeFile = createFileInSystem(folder, WRITE_SOLUTION + QUERY_FILE_EXT, writeSolutionSql);

		return successfullyMadeFile;
	}

	/**
	 * This method will look at the CSV file inside the folder created for this
	 * instance of the Timecard object, and it returns the content of it. If
	 * there is content, the user is still logged in from a previous session. If
	 * not, the user is up to date with their clock-in, clock-out cycles.
	 * 
	 * @param folder
	 * @return the string with their clock in information
	 * @throws IOException
	 */
	private String seeInsideCSVFile(File folder) throws InterruptedException, IOException {
		String csvPath = folder.getPath() + "/" + nameOfFile + RESULT_FILE_EXT;
		log("Starting the seeInsideCSVFile method, looking for " + csvPath);

		/*
		 * Creates the path to the CSV file in the system, and initializes the
		 * String object which will hold the data from the line in the CSV file.
		 */
		String csvLine = "";

		/*
		 * Creates a file object which points at the CSV file in the system.
		 */
		File csv = new File(csvPath);

		InterAppComm.waitForFileToExist(csv, connection, InterAppComm.MS_TO_WAIT, InterAppComm.MAX_CHECKS_TO_WAIT);

		if (csv.exists()) {
			log("File exists at " + csv.getAbsolutePath() + ".  Now reading.");
			BufferedReader csvReader = new BufferedReader(new FileReader(csv));

			csvLine = csvReader.readLine();

			csvReader.close();
		} else {
			log("ERROR: File " + nameOfFile + " does not exist here.");
		}

		/*
		 * analyzes the information in the csvLine string. If there is data in
		 * it, it returns the data. If not, it tells the system that the person
		 * is up to date.
		 */
		if (csvLine != null) {
			log("There was data in the file, returning it now.");

			// removes pipe characters in the string from the line
			csvLine = csvLine.replaceAll(PIPE_DELIMITER, "");
			return csvLine;
		} else {
			log("There was no data in the file.");
			return null;
		}
	}

	/*
	 * 
	 * 
	 * 
	 * 
	 * 
	 * ***********************BEGIN STANDARD METHODS********************
	 * 
	 * 
	 * 
	 * 
	 * 
	 */

	/**
	 * called at the first login if the employee did not log out from their
	 * previous session. This method determines whether today's date and time is
	 * closer to the clock in time compared to a set amount of hours past that
	 * date. It returns the formatted string of the date of whichever is closer,
	 * today's date, or the time a select number of hours past the clock in
	 * time.
	 * 
	 * @param earlier,
	 *            the clock in time and date
	 * @param later,
	 *            today's time and date
	 * @param maxClock,
	 *            the maximum amount of hours someone can be clocked in
	 * @return the array of time data in String format
	 * @throws ParseException
	 */
	private String[] getClockOutTime(String earlier, String later, int maxClock) throws ParseException {
		log("Starting the getClockOutTime method, which returns the time to say we clocked out at.");

		/*
		 * establishes the dates and times for the last clock in and today's
		 * date, and assigns variables to them
		 */
		DateFormat toUSTime = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US);
		Date earlyDate = toUSTime.parse(earlier);
		Date laterDate = toUSTime.parse(later);
		log("Clock in date is " + earlyDate + ".");

		/*
		 * finds the milliseconds of difference between the clock in time and
		 * the specified amount of hours after that clock in time
		 */
		long millisMaxClock = ((maxClock * 60) * 60) * 1000;
		log("Difference between clock in and " + maxClock + " hours later: " + millisMaxClock + " ms.");

		/*
		 * finds the milliseconds of difference between the clock in time and
		 * today's date and time
		 */
		long differenceBetweenDates = laterDate.getTime() - earlyDate.getTime();
		log("Difference between clock in and now: " + differenceBetweenDates + " ms.");

		/*
		 * compares the time difference from the clock in to today versus the
		 * time difference from clock in to the specified amount of hours after
		 * clock in
		 */
		long differenceToUse;
		String clockOutDate;
		if (differenceBetweenDates < millisMaxClock) {
			/*
			 * if this option is chosen, the current date is used to clock out
			 * with
			 */
			differenceToUse = differenceBetweenDates;
			clockOutDate = later;
		} else {
			/*
			 * if this option is chosen, the specified time after the clock in
			 * value is used to clock out with
			 */
			laterDate.setTime(earlyDate.getTime() + millisMaxClock);
			differenceToUse = millisMaxClock;
			clockOutDate = toUSTime.format(laterDate);
		}

		/*
		 * formats the selected clock out date into the usable string to split
		 * into an array
		 */
		String formattedClockOutDate = clockOutDate.replaceAll(" ", SEPARATOR);
		double differenceInHours = ((Double.parseDouble("" + differenceToUse) / 1000) / 60) / 60;

		formattedClockOutDate = formattedClockOutDate + SEPARATOR + differenceInHours;

		/*
		 * creates an array of time data by splitting the string at the
		 * SEPARATOR sequence
		 */
		String[] timeData = formattedClockOutDate.split("\\" + SEPARATOR);

		log("Clock out date set to " + timeData[0] + ", clock out time is " + timeData[1]
				+ ", the user was clocked in for " + timeData[2] + " hours.");

		/*
		 * returns the array
		 */
		return timeData;
	}

	/**
	 * Truncates a String object to seven characters.
	 * 
	 * @param hoursClockedIn
	 * @return the truncated String
	 */
	private String truncateHours(String hoursClockedIn) {
		String truncatedHours = hoursClockedIn;

		if (hoursClockedIn.length() > 7) {
			truncatedHours = hoursClockedIn.substring(0, 7);
		}

		return truncatedHours;
	}

	/**
	 * a helper method used by the makeXXXXXFile methods, this is where the file
	 * is actually created. The code is placed here so that it can be shared
	 * between those methods.
	 * 
	 * @param folder
	 * @param name
	 * @param sqlStatement
	 * @return whether the file was successfully created or not
	 */
	private boolean createFileInSystem(File folder, String name, String sqlStatement) {
		/*
		 * initializes boolean variable to return
		 */
		boolean successfullyMadeFile;

		/*
		 * attempts to create the file in the system
		 */
		try {
			/*
			 * if successful, the successfullyMadeFile variable is set to true
			 * and returned
			 */
			File file = new File(folder.getPath() + '/' + name);

			/*
			 * This deletes the file if one with the same name already exists in
			 * the same location. This is useful when clocking in to a work
			 * order, for example, where two different SQL Query files have to
			 * be created for two different operations, both getting the stock
			 * number and clocking in.
			 */
			if (file.exists()) {
				file.delete();
			}

			successfullyMadeFile = file.createNewFile();

			BufferedWriter fileWriter = new BufferedWriter(new FileWriter(file));
			fileWriter.write(sqlStatement);

			fileWriter.close();
		} catch (IOException err) {
			/*
			 * if an exception is thrown, the successfullyMadeFile variable is
			 * set to false and returned
			 */
			err.printStackTrace();
			successfullyMadeFile = false;
		}

		return successfullyMadeFile;
	}

	/**
	 * prints a message to the console with the date, time and Timecard string
	 * appended to it
	 * 
	 * @param message
	 */
	private void log(String message) {
		stepNumber++;
		Write.writeLine(APP_NAME, message, counter, stepNumber);
	}
}