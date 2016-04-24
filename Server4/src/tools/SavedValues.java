package tools;

public class SavedValues {
	
	private static final int PORT = 4242;
	private static final String AUTH_IP = "mobile.getrain.com";
	private static final int AUTH_PORT = 4141;
	private static final boolean DEBUG_MODE = true;
	private static final String CONTENT_SEP = "^";
	private static final String ITEM_SEP = "|";
	private static final String ITEM_DELIMITER = "\\" + ITEM_SEP;
    private static final String SPECIAL_LINE_SEP = "§¶§";
    private static final String UTF8_LINE_SEP = "<SPACE/>";
    private static final String UTF8_CARET_SYM = "<CARET/>";
    private static final String UTF8_POLE_SYM = "<POLE/>";
	private static final String GUI_NOTICE = "<html><body style='width:325px; "
			+ "padding:25px'>This is the server application.  It handles all of the "
			+ "requests from the mobile apps for Rainmaker Software.  Exit out of this "
			+ "window to stop the service.</body></html>";
	private static final long TIME_TO_REFRESH = 1800000;

	public static int getPort() {
		return PORT;
	}
	
	public static String getAuthIp() {
		return AUTH_IP;
	}
	
	public static int getAuthPort() {
		return AUTH_PORT;
	}
	
	public static boolean shouldShowLogs() {
		return DEBUG_MODE;
	}
	
	public static String getContentSeparator() {
		return CONTENT_SEP;
	}
	
	public static String getItemSeparator() {
		return ITEM_SEP;
	}
	
	public static String getItemDelimiter() {
		return ITEM_DELIMITER;
	}
	
	public static String getUTF8LineSep() {
		return UTF8_LINE_SEP;
	}
	
	public static String getUTF8CaretSym() {
		return UTF8_CARET_SYM;
	}
	
	public static String getUTF8PoleSym() {
		return UTF8_POLE_SYM;
	}
	
	public static String getSpecialLineSep() {
		return SPECIAL_LINE_SEP;
	}
	
	public static String getGUINotice() {
		return GUI_NOTICE;
	}
	
	public static long getTimeToRefresh() {
		return TIME_TO_REFRESH;
	}
}