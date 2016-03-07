package tools;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 
 * @author Nicholas Caputo, npocaputo@GMail.com, (847) 630 7370
 *
 */
public class TimeTools {
	
	private static final String APP_NAME = "TimeTools";
    private static final DateFormat forSql = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

	/**
	 * Returns the current date and time in specific formatting. Example:
	 * 02/17/2016,11:21:22 (MM/dd/yyyy,HH:mm:ss)
	 * 
	 * @return formatted string of current date and time
	 */
	public static String getCurrentDateTime() {
		Date date = new Date();
		SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy,HH:mm:ss");

		return format.format(date);
	}

	public static String[] getArrayDateTime() {
		String[] dateTime = getCurrentDateTime().split(",");
		return dateTime;
	}

	public static String getCurrentDate() {
		String[] dateAndTime = getArrayDateTime();
		return dateAndTime[0];
	}

	public static String getCurrentTime() {
		String[] dateAndTime = getArrayDateTime();
		return dateAndTime[1];
	}

	public static Date getDateObjFromString(String date) {
		try {
			return forSql.parse(date);
		} catch (ParseException err) {
			log(err.getMessage());
			return null;
		}
	}

	public static String getFormattedDate(Date date) {
		return forSql.format(date) + " 00:00:00.000";
	}

	public static String getToday() {
		return getFormattedDate(new Date());
	}

	public static String getYesterday() {
		Date yesterday = new Date(System.currentTimeMillis() - 86400000);
		return getFormattedDate(yesterday);
	}

	public static String getDay(int year, int month, int day) {
		if (isALegalDate(year, month, day)) {
			String monthToUse = "" + month;
			String dayToUse = "" + day;

			if (month < 9) {
				monthToUse = "0" + monthToUse;
			}

			if (day <= 9) {
				dayToUse = "0" + dayToUse;
			}

			return "" + year + '-' + monthToUse + '-' + dayToUse + " 00:00:00.000";
		} else {
			return "illegal_date";
		}
	}

	public static boolean isAfter(String earlier, String later) {
		Date beginning = TimeTools.getDateObjFromString(earlier);
		Date end = TimeTools.getDateObjFromString(later);

		return beginning.after(end);
	}

	/**
	 * Given a date (in common time), determines if it is a legal calendar date.
	 * Works with leap years.
	 *
	 * @param year,
	 *            the year
	 * @param month,
	 *            the month (1 - 12)
	 * @param day,
	 *            the day (1 - 31)
	 * @return if the date is legal
	 */
	private static boolean isALegalDate(int year, int month, int day) {
		// check date legality
		if ((month <= 7 && month % 2 == 0) || (month >= 8 && month % 2 == 1)) {
			if (month == 2) {
				if (isALeapYear(year)) {
					// System.out.println("Is a february in leap year");
					return day <= 29;
				} else {
					// System.out.println("Is a feburary in a normal year");
					return day <= 28;
				}
			}

			// System.out.println("Is an even month before July (but not
			// February) or an odd month after August");
			return day <= 30;
		} else {

			// System.out.println("Is an even month after August, or odd month
			// before july");
			return day <= 31;
		}
	}

	public static boolean isALeapYear(int year) {
		return (year % 4 == 0) && !(year % 100 == 0 && year % 400 != 0);
	}
	
	private static void log(String message) {
		Write.writeLine(APP_NAME, message);
	}

}
