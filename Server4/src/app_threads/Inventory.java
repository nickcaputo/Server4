package app_threads;
/**
 * @author Nicholas Caputo, (847) 630 7370
 * 
 * This class contains all the required commands to handle requests from the Mobile
 * Inventory app, and a new instance of it is created and destroyed for every request
 * from the Mobile Inventory app
 */

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;

import javax.imageio.ImageIO;

import main_threads.Server4;
import tools.DatabaseTools;
import tools.Folders;
import tools.Images;
import tools.InterAppComm;
import tools.Write;

/**
 * 
 * @author Nicholas Caputo, npocaputo@GMail.com, (847) 630 7370
 *
 */
public class Inventory {

	private static final String APP_NAME = "Inventory";

	private static final String SEPARATOR = "^";
	private static final String OUTGOING_SEP = "|&^&|";
	private static final int IMAGE_SIZE_LIMIT = 2048;
	private static final int WAIT_TIME_AFTER_ADDING_PICTURES = 2500;

	private String rmAppServerPath;
	private String rootPath;
	private String dbfPath = "";
	private String entry;
	private StringBuilder resultBuilder;
	private long counter;
	private Socket socket;
	private short stepNumber = 0;

	// for debugging purposes
	private long startTime;

	/**
	 * starts the main worker thread for Inventory, and keeps track of the
	 * command that it is given, and the counter number in the main Server class
	 * 
	 * @param entry
	 * @param counter
	 */
	public Inventory(String entry, long counter) {
		startTime = System.nanoTime();

		socket = Server4.getSocket(counter);
		this.entry = entry;
		this.counter = counter;
		rmAppServerPath = Server4.getRMAppServerPath();
		rootPath = rmAppServerPath.substring(0, rmAppServerPath.lastIndexOf('\\') + 1);
		connection.start();
	}

	/**
	 * the main worker thread for Inventory mobile. It executes a command line
	 * prompt to run a program in Visual FoxPro 9 to read the analyzer.dbf file
	 * for this specific customer, creates a string to send back, and the phone
	 * uses this to create a database to search through.
	 */
	Thread connection = new Thread() {
		public void run() {

			resultBuilder = new StringBuilder();

			try {
				log("Now processing with ID number " + counter + "...");

				/*
				 * split string into separate ones with command and username on
				 * it
				 */
				String[] data = entry.split("\\" + SEPARATOR);
				String command = data[0];
				String user = data[1];

				/*
				 * gets directory where client information is, such as Control5
				 * or Control6
				 */
				try {
					/*
					 * authenticates the user
					 */
					ResultSet authenticate = DatabaseTools.authenticateInventory(rmAppServerPath, user);

					if (authenticate.next()) {
						dbfPath = authenticate.getString("FilePath");
						if (dbfPath.contains(" ")) {
							dbfPath = dbfPath.replace(' ', '*');
						}
					}
				} catch (SQLException err) {
					err.printStackTrace();
				}

				/*
				 * use dbfPath to generate string of where to go for filePath
				 */
				char appendedChar = dbfPath.charAt(dbfPath.length() - 1);

				/*
				 * 
				 * performs required function based on command sent
				 * 
				 */
				if (command.equals("cloneDatabase")) {
					synchronized (this) {
						queryAnalyzer(appendedChar);
					}
				} else {
					String pictureFolder = "pictures" + counter + "/";
					if (command.equals("getPictures")) {
						/*
						 * gets the stock number from the original command
						 */
						int stockNumber = Integer.parseInt(data[2]);

						synchronized (this) {
							getPictures(pictureFolder, appendedChar, stockNumber);
						}
					} else if (command.equals("savePicture")) {
						/*
						 * gets the stockNumber, make, model, model year, and
						 * item name from original command
						 */
						int stockNumber = Integer.parseInt(data[2]);
						String make = data[3];
						String model = data[4];
						String modelYear = data[5];
						String itemName = data[6];

						boolean pathCreated = new File(pictureFolder).mkdir();

						if (pathCreated) {
							/*
							 * saving the byte array that came in from the
							 * connection to a picture file
							 */
							boolean pictureWasResized = savePictureToFile(pictureFolder, itemName);
							if (pictureWasResized) {
								itemName = itemName + "-Edited";
								log("Picture was edited, itemName was changed to " + itemName);
							}

							/*
							 * puts the newly created picture file into the
							 * database
							 */
							putPictureIntoDatabase(appendedChar, rootPath, pictureFolder, stockNumber, make, model,
									modelYear, itemName);

							/*
							 * deletes the folder with pictures on it after 2.5
							 * seconds (time for two shell commands to execute
							 * on VFP 9)
							 */
							synchronized (this) {
								this.wait(WAIT_TIME_AFTER_ADDING_PICTURES);
							}

							Folders.deleteFolder(pictureFolder, true);
						}

					}
				}

				/*
				 * closes the socket
				 */
				socket.close();

			} catch (IOException | InterruptedException err) {
				err.printStackTrace();
			} finally {

				long futureTime = System.nanoTime();

				long nanos = (futureTime - startTime);
				long millis = nanos / 1000000;
				log("This operation took " + nanos + " nanoseconds (" + millis + " ms).");

				/*
				 * thread self-destructs and is removed from the HashMap in the
				 * main class
				 */
				Server4.removeThread("Inventory", counter);
			}
		}
	};

	/**
	 * Executes the export.exe file which queries the database, reads the CSV
	 * file which is produced as a result, and then sends the data back to the
	 * device
	 * 
	 * @param appendedChar
	 * @throws InterruptedException
	 * @throws IOException
	 */
	private void queryAnalyzer(char appendedChar) throws InterruptedException, IOException {
		// get the analyzer file, stocktxt file, and tce_pik file
		String dbfLocation = "\"" + dbfPath + "\\ctrldbf" + appendedChar + "\\analyzer;" + dbfPath + "\\ctrldbf"
				+ appendedChar + "\\stocktxt;" + dbfPath + "\\ctrldbf" + appendedChar + "\\images\\tce_pik\" ";

		File inventoryFolder = new File("inventory" + counter);
		boolean createdFolder = inventoryFolder.mkdir();
		if (createdFolder) {
			String csvPath = inventoryFolder.getAbsolutePath() + "\\" + "output" + counter; // +
			// ".csv";
			String shellCommand = "\"" + rootPath + "export\" " + dbfLocation + "\"*" + rootPath + "query.txt\" \""
					+ csvPath + "\"";

			shellExecute(shellCommand);
			String csvLine = "";

			File csv = new File(csvPath + ".csv");

			InterAppComm.waitForFileToExist(csv, connection, InterAppComm.MS_TO_WAIT, InterAppComm.MAX_CHECKS_TO_WAIT);

			if (csv.exists()) {
				PrintWriter outToClient = new PrintWriter(socket.getOutputStream());

				String resultToSend = readCSV(csvLine, csvPath);

				log("Now sending " + resultToSend);
				outToClient.println(resultToSend);

				outToClient.close();
			} else {
				log("We could not find the file " + csvPath + ".csv");
			}
		}

		Folders.deleteFolder(inventoryFolder, true);
	}

	/**
	 * Performs File I/O and reads the CSV file stored for the item
	 * 
	 * @param csvLine
	 * @param csvPath
	 * @return
	 */
	private String readCSV(String csvLine, String csvPath) {
		try {
			BufferedReader csvReader = new BufferedReader(new FileReader(csvPath + ".csv"));

			while ((csvLine = csvReader.readLine()) != null) {
				resultBuilder.append(csvLine + OUTGOING_SEP);
			}

			csvReader.close();

		} catch (IOException err) {
			err.printStackTrace();
		}

		String resultRaw = resultBuilder.toString();
		String resultToSend = resultRaw.replaceAll("\"\"", "\"no data\"");

		return resultToSend;
	}

	/**
	 * runs the export.exe to query analyzer, compiles the pictures created into
	 * byte arrays and sends them over the network to the device before deleting
	 * them
	 * 
	 * @param appendedChar
	 * @param stockNumber
	 * @throws InterruptedException
	 * @throws IOException
	 */
	private void getPictures(String picturesFolder, char appendedChar, int stockNumber)
			throws InterruptedException, IOException {
		ArrayList<Integer> locationsOfFiles;
		int fileCounter = 1;

		String dbfParameter = "\"!" + dbfPath + "\\ctrldbf" + appendedChar + "\\images\\pictures\" ";
		String shellCommand = "\"" + rootPath + "export\" " + dbfParameter + " \"" + stockNumber + "\" \"" + rootPath
				+ picturesFolder + "\"";
		/*
		 * creates folder to put pictures into
		 */
		log("Creating folder pictures" + counter);
		boolean pathCreated = new File(picturesFolder).mkdir();

		/*
		 * executes shell command
		 */
		if (pathCreated) {
			shellExecute(shellCommand);

			log("looking for image " + picturesFolder + stockNumber + "-" + fileCounter + ".jpg");
			File image = new File(picturesFolder + stockNumber + "-" + fileCounter + ".jpg");
			int totalBytes = 0;
			locationsOfFiles = new ArrayList<>();

			InterAppComm.waitForFileToExist(image, connection, InterAppComm.MS_TO_WAIT,
					InterAppComm.MAX_CHECKS_TO_WAIT);

			if (image.exists()) {
				do {
					if (totalBytes < Integer.MAX_VALUE - (int) image.length()) {
						locationsOfFiles.add((int) image.length());
						log(image.getName() + " is " + image.length() + " bytes.");
						fileCounter++;
						totalBytes = totalBytes + (int) image.length();
						image = new File(picturesFolder + stockNumber + "-" + fileCounter + ".jpg");
					} else {
						break;
					}
				} while (image.exists());

				log("total size of all files being sent: " + totalBytes);
				log("total number of files: " + locationsOfFiles.size());

				byte[] files = convertFileToBytes(picturesFolder, stockNumber, locationsOfFiles, totalBytes);
				sendData(stockNumber, files, locationsOfFiles);
			}
		}

		// deletes the folder we just created
		Folders.deleteFolder(picturesFolder, true);
	}

	/**
	 * retrieves picture from the byte array coming in and saves the picture to
	 * a file on the system
	 * 
	 * @param stockNumber
	 * @param make
	 * @param model
	 * @param modelYear
	 * @param itemName
	 * @throws IOException
	 */
	private boolean savePictureToFile(String picturesFolder, String itemName) throws IOException {
		InputStream input = socket.getInputStream();
		DataInputStream dataInput = new DataInputStream(input);
		boolean resized = false;

		log("Retrieving file from connection now");

		/*
		 * gets file from dataInput and closes input streams
		 * 
		 * Implement a compression algorithm here to shorten the array having to
		 * be sent
		 */
		int sizeOfPicture = dataInput.readInt();
		log("The picture's size is " + sizeOfPicture + " bytes.");

		byte[] incomingFile = new byte[sizeOfPicture];

		if (sizeOfPicture > 0) {
			dataInput.readFully(incomingFile);
		}

		dataInput.close();
		input.close();

		log("The image is fully downloaded here");

		try {
			File image = new File(picturesFolder + itemName + ".jpg");
			FileOutputStream fileOutputStream = new FileOutputStream(image);

			log("Reading " + incomingFile.length + " bytes to " + image.getAbsolutePath());

			fileOutputStream.write(incomingFile);

			fileOutputStream.close();

			if (image.exists()) {
				resized = resizeImagesIfNecessary(picturesFolder, itemName, image);
			}
		} catch (FileNotFoundException err) {
			err.printStackTrace();
		}
		return resized;
	}

	/**
	 * checks the size of the image and resizes it if necessary
	 * 
	 * @param picturesFolder
	 * @param itemName
	 * @param image
	 * @throws IOException
	 */
	private boolean resizeImagesIfNecessary(String picturesFolder, String itemName, File image) throws IOException {
		log("Checking if image has to be resized");
		boolean changed = false;
		/*
		 * checks size of image and resizes if necessary
		 */
		BufferedImage picture = ImageIO.read(image);

		int height = picture.getHeight();
		int width = picture.getWidth();

		/*
		 * calculates percentage to reduce by and still maintain highest
		 * allowable quality
		 */
		if (height >= IMAGE_SIZE_LIMIT || width >= IMAGE_SIZE_LIMIT) {
			log("Proportionally resizing image");
			String outputLocation = picturesFolder + itemName + "-Edited.jpg";
			double percentToReduce;
			if (height >= width) {
				percentToReduce = (double) IMAGE_SIZE_LIMIT / height;
			} else {
				percentToReduce = (double) IMAGE_SIZE_LIMIT / width;
			}
			Images.resizeProportional(image, outputLocation, percentToReduce, width, height);
			changed = true;
		}
		return changed;
	}

	/**
	 * takes pictures created by the savePictureToFile method, creates text
	 * files to pass to the export.exe file and then adds them to the tce_pik
	 * and pictures tables
	 * 
	 * @param rootPath
	 * @param picturesFolder
	 * @param stockNumber
	 * @param make
	 * @param model
	 * @param modelYear
	 * @param itemName
	 * @throws IOException
	 */
	private void putPictureIntoDatabase(char appendedChar, String rootPath, String picturesFolder, int stockNumber,
			String make, String model, String modelYear, String itemName) throws IOException {
		BufferedWriter fileMaker = new BufferedWriter(new FileWriter(picturesFolder + "putIntoTcePik.txt"));
		fileMaker.write("INSERT INTO tce_pik (id, descrip, picture, stretch, pix_name, pix_kee, checked) values('"
				+ stockNumber + "', '" + make + " " + model + "', FILETOSTR([" + rootPath + picturesFolder + itemName
				+ ".jpg]), .F., '" + itemName + "', str(" + Calendar.getInstance().get(Calendar.YEAR) + "), .F.)");
		fileMaker.close();

		fileMaker = new BufferedWriter(new FileWriter(picturesFolder + "putIntoPictures.txt"));
		fileMaker
				.write("INSERT INTO pictures (stockno, mfgno, model, modyr, pk, picture, source, fname, checked) values('"
						+ stockNumber + "', '" + make + "', '" + model + "', '" + modelYear
						+ "', sys(2015), FILETOSTR([" + rootPath + picturesFolder + itemName + ".jpg]), 'S', '"
						+ itemName + "', .F.)");
		fileMaker.close();

		log("Adding picture to proper tables");

		String dbfTcePik = dbfPath + "\\ctrldbf" + appendedChar + "\\images\\tce_pik\" ";
		String dbfPictures = dbfPath + "\\ctrldbf" + appendedChar + "\\images\\pictures\" ";
		String addToTcePik = "\"" + rootPath + "export\" \"" + dbfTcePik + "\"*" + rootPath + picturesFolder
				+ "putIntoTcePik.txt\" \"execute\"";
		String addToPictures = "\"" + rootPath + "export\" \"" + dbfPictures + "\"*" + rootPath + picturesFolder
				+ "putIntoPictures.txt\" \"execute\"";

		shellExecute(addToTcePik);
		shellExecute(addToPictures);
	}

	/**
	 * converts an image to a byte array so it can be sent
	 * 
	 * @param file
	 * @return
	 */
	private byte[] convertFileToBytes(String pictureFolder, int stockNumber, ArrayList<Integer> locationsOfFiles,
			int totalBytes) {
		byte[] allFilesToSend = new byte[totalBytes];
		int fileOffset = 0;

		for (int index = 0; index < locationsOfFiles.size(); index++) {
			File file = new File(pictureFolder + stockNumber + "-" + (index + 1) + ".jpg");
			if (file.exists()) {
				try {
					FileInputStream fileInputStream = new FileInputStream(file);
					log("reading bytes starting at " + fileOffset + " for " + locationsOfFiles.get(index) + " bytes");
					fileInputStream.read(allFilesToSend, fileOffset, locationsOfFiles.get(index));

					fileOffset = fileOffset + locationsOfFiles.get(index);

					fileInputStream.close();
				} catch (FileNotFoundException err) {
					err.printStackTrace();
					log("Error finding the file");
				} catch (IOException err) {
					err.printStackTrace();
					log("Error reading the file");
				}
			}
		}

		log("Sending " + allFilesToSend.length + " bytes of data to " + socket.getInetAddress());
		return allFilesToSend;
	}

	/**
	 * sends the byte array to the device, where the device recreates the image
	 * file
	 * 
	 * @param allFilesToSend
	 * @param start
	 * @param length
	 */
	private void sendData(int stockNumber, byte[] allFilesToSend, ArrayList<Integer> locationsOfFiles) {
		try {
			OutputStream out = socket.getOutputStream();
			DataOutputStream dataSending = new DataOutputStream(out);

			int numberOfFiles = locationsOfFiles.size();

			dataSending.writeInt(allFilesToSend.length);
			dataSending.writeInt(stockNumber);
			dataSending.writeInt(numberOfFiles);
			for (int index = 0; index < numberOfFiles; index++) {
				dataSending.writeInt(locationsOfFiles.get(index));
			}

			log("Length of byte array is " + allFilesToSend.length + ", sending to client at "
					+ socket.getInetAddress());
			if (allFilesToSend.length > 0) {
				/*
				 * sends the byte array
				 */
				dataSending.write(allFilesToSend);
			}
		} catch (IOException err) {
			err.printStackTrace();
		}
	}

	/**
	 * executes a shell command on the system
	 * 
	 * @param command,
	 *            what to run in the system shell
	 * @throws IOException
	 */
	private void shellExecute(String command) throws IOException {
		log("Executing command " + command);
		Runtime.getRuntime().exec(command);
	}

	/**
	 * prints a message to the console with the date, time and Inventory string
	 * appended to it
	 * 
	 * @param message
	 */
	private void log(String message) {
		stepNumber++;
		Write.writeLine(APP_NAME, message, counter, stepNumber);
	}
}