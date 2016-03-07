package tools;

import java.io.File;

public class InterAppComm {

	private static final String CLASS_SIG = "InterAppComm";

	public static final byte MAX_CHECKS_TO_WAIT = 25;
	public static final short MS_TO_WAIT = 100;

	public static void waitForFileToExist(File file, Thread thread, short timeToWait, byte turnsToWait) {
		byte counter = 0;
		try {
			while (!file.exists() && counter <= turnsToWait) {
				synchronized (thread) {
					thread.wait(timeToWait);
				}
				counter++;
			}
		} catch (InterruptedException err) {
			err.printStackTrace();
		}
	}

	/**
	 * Has the thread wait a specified amount of time, or 100 ms if null is
	 * passed.
	 * 
	 * @param ms
	 */
	public static void sleep(Short ms) {
		try {
			if (ms != null) {
				Thread.sleep(ms);
			} else {
				Thread.sleep(MS_TO_WAIT);
			}
		} catch (InterruptedException err) {
			err.printStackTrace();
		}
	}

	/**
	 * Convenience method. Converts the input into a Short object and calls the
	 * wait(Short ms) method.
	 * 
	 * @param ms,
	 *            ms to wait
	 */
	public static void sleep(short ms) {
		sleep(new Short(ms));
	}

	/**
	 * Allows you to simply write a number and call the sleep method, it will
	 * convert it to a Short and enforce a limit of 32 seconds to wait.
	 * 
	 * @param msToConvert, the time you wish to wait.
	 */
	public static void sleep(long msToConvert) {
		if (msToConvert > Short.MAX_VALUE) {
			Write.writeLine(CLASS_SIG, "Entered too long of a value to wait, waiting 32 seconds instead...");
			sleep(Short.MAX_VALUE);
		} else {
			sleep((short) msToConvert);
		}
	}

	/**
	 * Waits 100 ms, as if you passed null to the sleep(Short ms) method.
	 */
	public static void sleep() {
		sleep(new Short(MS_TO_WAIT));
	}
}
