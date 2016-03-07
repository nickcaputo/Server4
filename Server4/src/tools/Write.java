package tools;

import java.time.LocalDateTime;

public class Write {

	private static final boolean SHOW_LOGS = true;

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
		if (SHOW_LOGS) {
			System.out.println('[' + LocalDateTime.now().toString().replace('T', ' ') + " : " + appName + " with ID "
					+ counter + " (Step " + stepNumber + ")] " + message);
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
		if (SHOW_LOGS) {
			System.out
					.println('[' + LocalDateTime.now().toString().replace('T', ' ') + " : " + appName + "] " + message);
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
		if (SHOW_LOGS) {
			System.out.println('[' + System.currentTimeMillis() + " : " + appName + " with ID " + counter + " (Step "
					+ stepNumber + ")] " + message);
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
		if (SHOW_LOGS) {
			System.out.println('[' + System.currentTimeMillis() + " : " + appName + "] " + message);
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