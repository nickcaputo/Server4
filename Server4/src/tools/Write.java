package tools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;

public class Write {

	private static boolean logFileInitialized = false;
	private static File log;
	private static FileWriter logWriter;

	/**
	 * This method creates an external file to write log messages with.
	 */
	private static void initializeLogFile() {
		if (!logFileInitialized) {
			try {
				log = new File(System.currentTimeMillis() + "_log.log");
				logWriter = new FileWriter(log);
				log.createNewFile();
				logFileInitialized = true;
			} catch (IOException err) {
				err.printStackTrace();
				logFileInitialized = false;
			}
		}
	}

	/**
	 * This method takes a string that would normally be printed out to the
	 * console and prints it to an external file.
	 * 
	 * @param message,
	 *            the string to print
	 */
	private static void writeToLogFile(String message) {
		initializeLogFile();

		if (logFileInitialized) {
			try {
				logWriter.write(message + System.lineSeparator());
			} catch (IOException err) {
				err.printStackTrace();
			}
		}
	}

	/**
	 * prints a message to the console with the date, time, app label, server
	 * counter, and app stepNumber appended to it
	 * 
	 * @param appName
	 *            application calling this method
	 * @param message
	 *            the message to display
	 * @param counter
	 *            the server counter
	 * @param stepNumber
	 *            the step number for the app
	 */
	public static void writeLine(String appName, String message, long counter, short stepNumber) {
		String toPrint = '[' + LocalDateTime.now().toString().replace('T', ' ') + " : " + appName + " with ID "
				+ counter + " (Step " + stepNumber + ")] " + message;
		if (SavedValues.shouldShowLogs()) {
			System.out.println(toPrint);
			writeToLogFile(toPrint);
		}
	}

	/**
	 * Convenience method, it converts the double to a string and calls the
	 * writeLine method.
	 * 
	 * @param appName
	 *            application calling this method
	 * @param message
	 *            the message to display
	 * @param counter
	 *            the server counter
	 * @param stepNumber
	 *            the step number for the app
	 */
	public static void writeLine(String appName, double message, long counter, short stepNumber) {
		writeLine(appName, "" + message, counter, stepNumber);
	}

	/**
	 * Convenience method, it converts the long to a string and calls the
	 * writeLine method.
	 * 
	 * @param appName
	 *            application calling this method
	 * @param message
	 *            the message to display
	 * @param counter
	 *            the server counter
	 * @param stepNumber
	 *            the step number for the app
	 */
	public static void writeLine(String appName, long message, long counter, short stepNumber) {
		writeLine(appName, "" + message, counter, stepNumber);
	}

	/**
	 * Writes a line of text to the screen displaying the message, app name,
	 * date and time.
	 * 
	 * @param appName
	 *            application calling this method
	 * @param message
	 *            the message to display
	 */
	public static void writeLine(String appName, String message) {
		String toPrint = '[' + LocalDateTime.now().toString().replace('T', ' ') + " : " + appName + "] " + message;
		if (SavedValues.shouldShowLogs()) {
			System.out.println(toPrint);
			writeToLogFile(toPrint);
		}
	}

	/**
	 * Convenience method. It converts the double into a string and calls the
	 * writeLine method with the same amount of parameters.
	 * 
	 * @param appName
	 *            application calling this method
	 * @param message
	 *            the message to display
	 */
	public static void writeLine(String appName, double message) {
		writeLine(appName, "" + message);
	}

	/**
	 * Convenience method. It converts the long into a string and calls the
	 * writeLine method with the same amount of parameters.
	 * 
	 * @param appName
	 *            application calling this method
	 * @param message
	 *            the message to display
	 */
	public static void writeLine(String appName, long message) {
		writeLine(appName, "" + message);
	}

	/**
	 * prints a message to the console with the time in ms, app label, server
	 * counter, and app stepNumber appended to it
	 * 
	 * @param appName
	 *            application calling this method
	 * @param message
	 *            the message to display
	 * @param counter
	 *            the server counter
	 * @param stepNumber
	 *            the step number for the app
	 */
	public static void quickWrite(String appName, String message, long counter, short stepNumber) {
		if (SavedValues.shouldShowLogs()) {
			String toPrint = '[' + System.currentTimeMillis() + " : " + appName + " with ID " + counter + " (Step "
					+ stepNumber + ")] " + message;
			System.out.println(toPrint);
			writeToLogFile(toPrint);
		}
	}

	/**
	 * Convenience method, it converts the double to a string and calls the
	 * quickWrite method.
	 * 
	 * @param appName
	 *            application calling this method
	 * @param message
	 *            the message to display
	 * @param counter
	 *            the server counter
	 * @param stepNumber
	 *            the step number for the app
	 */
	public static void quickWrite(String appName, double message, long counter, short stepNumber) {
		quickWrite(appName, "" + message, counter, stepNumber);
	}

	/**
	 * Convenience method, it converts the long to a string and calls the
	 * quickWrite method.
	 * 
	 * @param appName
	 *            application calling this method
	 * @param message
	 *            the message to display
	 * @param counter
	 *            the server counter
	 * @param stepNumber
	 *            the step number for the app
	 */
	public static void quickWrite(String appName, long message, long counter, short stepNumber) {
		quickWrite(appName, "" + message, counter, stepNumber);
	}

	/**
	 * Writes a line of text to the screen displaying the message, app name, and
	 * time in ms.
	 * 
	 * @param appName
	 *            application calling this method
	 * @param message
	 *            the message to display
	 */
	public static void quickWrite(String appName, String message) {
		if (SavedValues.shouldShowLogs()) {
			String toPrint = '[' + System.currentTimeMillis() + " : " + appName + "] " + message;
			System.out.println(toPrint);
		}
	}

	/**
	 * Convenience method. It converts the double into a string and calls the
	 * quickWrite method with the same amount of parameters.
	 * 
	 * @param appName
	 *            application calling this method
	 * @param message
	 *            the message to display
	 */
	public static void quickWrite(String appName, double message) {
		quickWrite(appName, "" + message);
	}

	/**
	 * Convenience method. It converts the long into a string and calls the
	 * quickWrite method with the same amount of parameters.
	 * 
	 * @param appName
	 *            application calling this method
	 * @param message
	 *            the message to display
	 */
	public static void quickWrite(String appName, long message) {
		quickWrite(appName, "" + message);
	}

}