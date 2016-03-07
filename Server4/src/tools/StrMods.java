package tools;

public class StrMods {

	/**
	 * Given a first, middle and last name, returns the value of that user's
	 * properly formatted name. The returned result will be null if the first or
	 * last name is null or empty.
	 * 
	 * @param first, the first name
	 * @param middle, the middle name or initial
	 * @param last, the last name
	 * @return the properly formatted name
	 */
	public static String formatName(String first, String middle, String last) {
		first = checkForValue(first);
		middle = checkForValue(middle);
		last = checkForValue(last);

		if (first == null || last == null)
			return null;

		return middle != null ? first + ' ' + middle + ' ' + last : first + ' ' + last;
	}

	/**
	 * Checks a string to make sure it is not null, is not empty, or does not
	 * equal the word "null", and returns the string if it passes these tests,
	 * or null otherwise.
	 * 
	 * @param inQuestion,
	 *            the String to check
	 * @return
	 */
	public static String checkForValue(String inQuestion) {
		return inQuestion == null || inQuestion.trim().isEmpty() || inQuestion.equals("null") ? null : inQuestion;
	}

    public static void append(StringBuilder builder, String toAppend, String separator) {
        if (toAppend != null) builder.append(toAppend).append(separator);
    }

    public static void append(StringBuilder builder, boolean toAppend, String separator) {
        append(builder, "" + toAppend, separator);
    }

}
