package tools;

/**
 * This class contains all of the queries and commands which are executed 
 * upon their respective databases.  It uses PreparedStatements to execute 
 * the commands, and the methods either return an integer detailing how 
 * many rows were updated, if an INSERT or UPDATE command is issued, or 
 * they will return a ResultSet with the requested information if a SELECT 
 * query is given.  It was first written on 11/9/2015.
 * 
 * @author Nicholas Caputo
 */

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import main_threads.Server4;

/**
 * 
 * @author Nicholas Caputo, npocaputo@GMail.com, (847) 630 7370
 *
 */
public class DatabaseTools {

	private static final String APP_NAME = "DatabaseTools";

	/**
	 * provides the connection to the database that the system will use to query
	 * the database
	 * 
	 * @param dataPass
	 * @param path
	 * @return the connection to the database
	 * @throws SQLException
	 */
	public static Connection initialize(String dataPass, String path) throws SQLException {
		Connection root = DriverManager.getConnection("jdbc:ucanaccess://" + path + ";memory=false", "", dataPass);
		return root;
	}

	/**
	 * Gets the list of databases for Prospector Pro installations on this
	 * server.
	 * 
	 * @param path
	 * @return
	 * @throws SQLException
	 */
	public static ResultSet findAllDatabases(String path) throws SQLException {
		log("Starting the findDatabases method for Prospector Pro installations on this machine.");

		Connection root = initialize("", path);
		String findDatabaseSql = "SELECT DISTINCT FilePath FROM RemoteClientInfo";

		PreparedStatement getDatabases = root.prepareStatement(findDatabaseSql, ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);

		ResultSet databaseList = getDatabases.executeQuery();

		root.close();

		return databaseList;
	}

	/*
	 * 
	 * 
	 * 
	 * 
	 * *****************START MAIN SERVER METHODS***********************
	 * 
	 * 
	 * 
	 * 
	 */

	public static ResultSet getTopDatabases(String path, String dataPass) throws SQLException {
		Connection root = initialize(dataPass, path);
		String findDatabases = "SELECT TOP 1 FilePath FROM RemoteClientInfo UNION SELECT FilePath FROM RemoteClientInfo "
				+ "WHERE NOT FilePath=(SELECT TOP 1 FilePath FROM RemoteClientInfo)";

		PreparedStatement getTopDatabases = root.prepareStatement(findDatabases, ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);

		ResultSet databaseList = getTopDatabases.executeQuery();

		root.close();

		return databaseList;
	}

	public static ResultSet getSalespersonName(String path, String dataPass, String salesId, String locationId)
			throws SQLException {
		Connection root = initialize(dataPass, path);
		String findName = "SELECT Sales.*, Location.LocName FROM Sales LEFT JOIN Location ON Sales.LocId=Location.LocId WHERE SlsId=? AND LocId=?";

		PreparedStatement getName = root.prepareStatement(findName, ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);

		getName.setString(1, salesId);
		getName.setString(2, locationId);

		ResultSet salespersonName = getName.executeQuery();

		root.close();

		return salespersonName;
	}

	public static ResultSet getSalesAndLocationIds(String path, String dataPass) throws SQLException {
		Connection root = initialize(dataPass, path);
		String getIdsSql = "SELECT DISTINCT SlsId, LocId FROM Sales";

		PreparedStatement getIds = root.prepareStatement(getIdsSql, ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);

		ResultSet ids = getIds.executeQuery();

		root.close();

		return ids;
	}

	/*
	 * 
	 * 
	 * 
	 * ****************START INITIALIZE DATA METHODS***********************
	 * 
	 * 
	 * 
	 * 
	 */

	/**
	 * finds the correct Prospector Pro database for this user
	 * 
	 * @param user
	 * @param path
	 * @return the resultSet for this user
	 * @throws SQLException
	 */
	public static ResultSet findDatabase(String user, String path) throws SQLException {
		log("Starting the findDatabase method for Prospector Pro.");
		Connection root = initialize("", path);
		String findDatabaseSql = "SELECT * FROM RemoteClientInfo WHERE UserName=?";
		PreparedStatement findDatabase = root.prepareStatement(findDatabaseSql, ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);

		findDatabase.setString(1, user);

		ResultSet databaseLocation = findDatabase.executeQuery();

		root.close();

		return databaseLocation;
	}

	/*
	 * 
	 * 
	 * 
	 * *******************START PROSPECTOR PRO MOBILE METHODS*****************
	 * 
	 * 
	 * 
	 * 
	 */

	/**
	 * performs a quick search of contacts up to a specified amount
	 * 
	 * @param dataPass
	 * @param path
	 * @param amount
	 * @return the first however many contacts
	 * @throws SQLException
	 */
	public static ResultSet quickSearchContacts(String dataPass, String path, int amount) throws SQLException {
		log("Starting the quickSearchContacts method for Prospector Pro.");

		Connection root = initialize(dataPass, path);

		log("Searching the top " + amount + " contacts in Prospector Pro.");
		String quickSearchSql = "SELECT TOP ? * FROM Contact ORDER BY Score DESC";
		PreparedStatement quickSearch = root.prepareStatement(quickSearchSql, ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);

		quickSearch.setInt(1, amount);

		ResultSet quickSearchInformation = quickSearch.executeQuery();

		root.close();

		return quickSearchInformation;
	}

	/**
	 * accepts a search query and searches Contact for it, returning the
	 * results. This is used when the user submits a query from the Search bar
	 * in the Android app (clicks the magnifying glass on the toolbar, types in
	 * a query and presses enter).
	 * 
	 * @param dataPass
	 * @param path
	 * @param searchQuery
	 * @return the search results
	 * @throws SQLException
	 */
	public static ResultSet searchContacts(String dataPass, String path, String searchQuery) throws SQLException {
		log("Starting the searchContacts method for Prospector Pro.");

		searchQuery = searchQuery.replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![")
				.replace("@", "!@").replace(".", "!.");

		Connection root = initialize(dataPass, path);
		String searchSql = "SELECT * FROM Contact WHERE Fname LIKE ? OR Lname LIKE ? OR Fname + Space(1) + Lname LIKE "
				+ "? OR Fname + Space(1) + Middle + Space(1) + Lname LIKE ? OR Company LIKE ? OR "
				+ "Address LIKE ? OR City LIKE ? OR WorkPhone LIKE ? OR HomPhone LIKE ? OR OtherPhone "
				+ "LIKE ? OR Email LIKE ? ESCAPE '!'";
		PreparedStatement search = root.prepareStatement(searchSql, ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);

		for (int i = 1; i <= 11; i++) {
			search.setString(i, "%" + searchQuery + "%");
		}

		ResultSet searchResults = search.executeQuery();

		root.close();

		return searchResults;
	}

	/**
	 * quick searches all activities for a specific customer, performed after
	 * someone selects a customer, and selects the "Activities" button on the
	 * bottom of the screen
	 * 
	 * @param dataPass
	 * @param path
	 * @param customerId
	 * @return
	 * @throws SQLException
	 */
	public static ResultSet searchActivities(String dataPass, String path, String customerId) throws SQLException {
		log("Starting the searchActivities method for Prospector Pro.");

		Connection root = initialize(dataPass, path);
		String qsActivitiesSql = "SELECT Activities.*, Contact.Fname, Contact.Middle, Contact.Lname, Contact.OtherPhone, "
				+ "Contact.HomPhone, Contact.WorkPhone, Contact.Email, Contact.Address, Contact.Company FROM Activities "
				+ "LEFT JOIN Contact ON Activities.CustId=Contact.CustId WHERE Activities.CustId=? AND Hide='False' "
				+ "ORDER BY Activities.SchedDate DESC";
		PreparedStatement searchSpecificActivities = root.prepareStatement(qsActivitiesSql,
				ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
		searchSpecificActivities.setString(1, customerId);

		ResultSet specificActivities = searchSpecificActivities.executeQuery();

		root.close();

		return specificActivities;
	}

	/**
	 * **********************************EXPERIMENTAL NEW QUERY
	 * 
	 * @param dataPass,
	 *            the password to the database
	 * @param path,
	 *            the path to the database in the file system
	 * @param customerId,
	 *            the customer ID to search for
	 * @return the result of simple activities
	 * @throws SQLException
	 */
	public static ResultSet searchActivities_noQueries(String dataPass, String path, String customerId)
			throws SQLException {
		log("Starting the new method of searching the database!");

		Connection root = initialize(dataPass, path);

		String activitiesSearchSQL = "SELECT CustId, CallId AS SpecificId, SlsId, Phone AS Essentials, "
				+ "SchedDate AS ScheduledDate, CompletedDate, Time AS ScheduledTime, 'NotCorr' AS "
				+ "TypeCorr, Notes, 'Call' AS Type FROM Calls WHERE CustId=? UNION SELECT CustId, "
				+ "CorrId AS SpecificId, SlsId, DocPath AS Essentials, CorrSch AS ScheduledDate, "
				+ "CorrComp AS CompletedDate, NULL AS ScheduledTime, TypeCorr, Memo AS Notes, "
				+ "'Correspondence' AS Type FROM Correspondence WHERE CustId=? UNION SELECT CustId, "
				+ "TaskId AS SpecificId, SlsId, Location AS Essentials, DateSch AS ScheduledDate, "
				+ "DateCompleted AS CompletedDate, TimeSch AS ScheduledTime, 'NotCorr' AS TypeCorr, "
				+ "Notes, 'Task' AS Type FROM Tasks WHERE CustId=? ORDER BY ScheduledDate DESC";

		PreparedStatement statement = root.prepareStatement(activitiesSearchSQL);

		for (int i = 1; i <= 3; i++) {
			statement.setString(i, customerId);
		}

		ResultSet activitySimple = statement.executeQuery();

		return activitySimple;
	}

	/**
	 * Queries the contact table for a user's name, given their customerId, and
	 * formats the name for you.
	 * 
	 * @param dataPass,
	 *            the password to the database
	 * @param path,
	 *            the path to the database
	 * @param customerId,
	 *            the user's customerId
	 * @return the properly formatted name
	 * @throws SQLException,
	 *             in case of a bad connection or a malformed SQL statement.
	 */
	public static String getCustomerNameFromId(String dataPass, String path, String customerId) throws SQLException {
		Connection root = initialize(dataPass, path);

		String getNameSQL = "SELECT Fname, Middle, Lname FROM Contact WHERE CustId=?";
		PreparedStatement statement = root.prepareStatement(getNameSQL);
		statement.setString(1, customerId);
		ResultSet set = statement.executeQuery();

		if (set.next()) {
			String first = set.getString("Fname");
			String middle = set.getString("Middle");
			String last = set.getString("Lname");

			return StrMods.formatName(first, middle, last);
		} else {
			return null;
		}
	}

	public static ResultSet searchDateRange_noQueries(String dataPass, String path, String beginningDate,
			String endingDate) throws SQLException {
		log("Starting the new method of searching the database by date!");

		Connection root = initialize(dataPass, path);

		String activitiesSearchSQL = "SELECT CustId, CallId AS SpecificId, SlsId, Phone AS Essentials, "
				+ "SchedDate AS ScheduledDate, CompletedDate, Time AS ScheduledTime, 'NotCorr' AS "
				+ "TypeCorr, Notes, 'Call' AS Type FROM Calls WHERE SchedDate BETWEEN ? AND ? UNION "
				+ "SELECT CustId, CorrId AS SpecificId, SlsId, DocPath AS Essentials, CorrSch AS "
				+ "ScheduledDate, CorrComp AS CompletedDate, NULL AS ScheduledTime, TypeCorr, Memo AS "
				+ "Notes, 'Correspondence' AS Type FROM Correspondence WHERE CorrSch BETWEEN ? "
				+ "AND ? UNION SELECT CustId, TaskId AS SpecificId, SlsId, Location AS Essentials, "
				+ "DateSch AS ScheduledDate, DateCompleted AS CompletedDate, TimeSch AS ScheduledTime, "
				+ "'NotCorr' AS TypeCorr, Notes, 'Task' AS Type FROM Tasks WHERE DateSch BETWEEN "
				+ "? AND ? ORDER BY ScheduledDate DESC";

		PreparedStatement statement = root.prepareStatement(activitiesSearchSQL);

		for (int i = 1; i <= 6; i++) {
			if (i % 2 == 1) { // i is an odd number
				statement.setString(i, beginningDate);
			} else {
				statement.setString(i, endingDate);
			}
		}

		ResultSet activitySimple = statement.executeQuery();

		return activitySimple;
	}

	public static ResultSet searchSingleDate_noQueries(String dataPass, String path, String date) throws SQLException {
		return searchDateRange_noQueries(dataPass, path, date, date);
	}

	/**
	 * searches activities between a beginning date and an ending date, returns
	 * the results
	 * 
	 * @param dataPass
	 * @param path
	 * @param beginningDate
	 * @param endingDate
	 * @return list of activities in date range
	 * @throws SQLException
	 */
	public static ResultSet searchDateRange(String dataPass, String path, String beginningDate, String endingDate)
			throws SQLException {
		log("searchDateRange method starting for Prospector Pro.");

		Connection root = initialize(dataPass, path);
		String timeSearchSql = "SELECT Activities.*, Contact.Fname, Contact.Middle, Contact.Lname, "
				+ "Contact.OtherPhone, Contact.HomPhone, Contact.WorkPhone, Contact.Email, "
				+ "Contact.Address, Contact.Company FROM Activities LEFT JOIN Contact ON "
				+ "Activities.CustId=Contact.CustId WHERE SchedDate BETWEEN ? AND ?";
		PreparedStatement timeRangeSearch = root.prepareStatement(timeSearchSql, ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);

		timeRangeSearch.setString(1, beginningDate);
		timeRangeSearch.setString(2, endingDate);

		ResultSet timeRangeResults = timeRangeSearch.executeQuery();

		root.close();

		return timeRangeResults;
	}

	/**
	 * searches for activities occurring on a single date
	 * 
	 * @param dataPass
	 * @param path
	 * @param date
	 * @return the list of activities scheduled on that date
	 * @throws SQLException
	 */
	public static ResultSet searchSingleDate(String dataPass, String path, String date) throws SQLException {
		log("Modified searchSingleDate method for Prospector Pro starting.");

		return searchDateRange(dataPass, path, date, date);
	}

	/**
	 * gets all required information to display an activity on the device
	 * 
	 * @param user
	 * @param path
	 * @param dataPass
	 * @param activityId
	 * @return activity information
	 * @throws SQLException
	 */
	public static ResultSet getActivity(String path, String dataPass, String activityId) throws SQLException {
		log("Starting the getActivity method for Prospector Pro.");

		Connection root = initialize(dataPass, path);

		String selectActivitySql = "SELECT Activities.*, Contact.Fname, Contact.Middle, "
				+ "Contact.Lname, Contact.OtherPhone, Contact.HomPhone, Contact.WorkPhone,"
				+ " Contact.Email, Contact.Address, Contact.Company FROM Activities LEFT JOIN"
				+ " Contact ON Activities.CustId=Contact.CustId WHERE ActivityID=?";
		PreparedStatement selectActivity = root.prepareStatement(selectActivitySql, ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);
		selectActivity.setString(1, activityId);

		ResultSet activity = selectActivity.executeQuery();

		root.close();

		return activity;
	}

	/**
	 * returns all required information to display a contact on the device
	 * 
	 * @param user
	 * @param path
	 * @param dataPass
	 * @param customerId
	 * @return contact information
	 * @throws SQLException
	 */
	public static ResultSet getContact(String path, String dataPass, String customerId) throws SQLException {
		log("getContact method for Prospector Pro starting.");

		Connection root = initialize(dataPass, path);

		String selectContactSql = "SELECT * FROM Contact WHERE CustId=?";
		PreparedStatement selectContact = root.prepareStatement(selectContactSql, ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);
		selectContact.setString(1, customerId);

		ResultSet contact = selectContact.executeQuery();

		root.close();

		return contact;
	}

	/**
	 * returns the most recent note for an activity
	 * 
	 * @param path
	 * @param dataPass
	 * @param activityId
	 * @return most recent note for an activity
	 * @throws SQLException
	 */
	public static ResultSet getActivityNote(String path, String dataPass, String activityId) throws SQLException {
		log("getActivityNote method for Prospector Pro starting for activityId " + activityId + '.');

		Connection root = initialize(dataPass, path);

		String getNotesSql = "SELECT TOP 1 * FROM Notes WHERE NoteID LIKE ? ORDER BY NoteID DESC";
		PreparedStatement getNotes = root.prepareStatement(getNotesSql, ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);
		getNotes.setString(1, "%" + activityId + "%");

		ResultSet note = getNotes.executeQuery();

		log("There are " + getRowCount(note) + " rows in this resultSet.");

		root.close();

		return note;
	}

	public static ResultSet getNotesInformation(String path, String dataPass, String customerId) throws SQLException {
		log("getNotesInformation method starting now with customer id " + customerId);

		Connection root = initialize(dataPass, path);
		String getFromNotes = "SELECT TOP 1 NoteID, OrigNoteID, Memo FROM Notes WHERE "
				+ "NoteID LIKE ? ORDER BY NoteID DESC";
		PreparedStatement getNotesStatement = root.prepareStatement(getFromNotes, ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);

		getNotesStatement.setString(1, "%" + customerId + "%");

		ResultSet notesList = getNotesStatement.executeQuery();

		root.close();

		return notesList;
	}

	/**
	 * gets a new autoNumber from the MasterID table in the database, selects it
	 * in the table, deletes the entry we just made, and then returns the result
	 * 
	 * @param path
	 * @param dataPass
	 * @return amount of rows updated, should be 1
	 * @throws SQLException
	 */
	public static ResultSet getNewAutoNumber(String path, String dataPass) throws SQLException {
		log("getNewAutoNumber method for Prospector Pro starting now.");

		Connection root = initialize(dataPass, path);

		/*
		 * creates autoNumber from database
		 */
		String createIdSql = "INSERT INTO MasterID(RequestedBy) VALUES(1)";
		PreparedStatement autoNumber = root.prepareStatement(createIdSql, ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);
		autoNumber.executeUpdate();

		/*
		 * selects autoNumber from database
		 */
		String getAutoNumber = "SELECT AutoNumber FROM MasterID WHERE RequestedBy='1'";
		PreparedStatement retrieveAutoNumber = root.prepareStatement(getAutoNumber, ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);
		ResultSet autoNumberResult = retrieveAutoNumber.executeQuery();

		/*
		 * deletes entry we just made in MasterID
		 */
		String deleteEntry = "DELETE FROM MasterID WHERE RequestedBy='1'";
		PreparedStatement deleteFromMasterID = root.prepareStatement(deleteEntry, ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);
		deleteFromMasterID.executeUpdate();

		root.close();

		return autoNumberResult;
	}

	/*
	 * *******************BEGIN EDIT COMMANDS***************************
	 */

	/**
	 * edits a contact in the database when it is edited from the "Edit contact"
	 * screen
	 * 
	 * @param path
	 * @param dataPass
	 * @param customerId
	 * @param fName
	 * @param middle
	 * @param lName
	 * @param cellPhone
	 * @param homePhone
	 * @param workPhone
	 * @param email
	 * @param company
	 * @param address
	 * @param city
	 * @param state
	 * @param zipCode
	 * @return the integer with the number of rows updated
	 * @throws SQLException
	 */
	public static int editContact(String path, String dataPass, String customerId, String fName, String middle,
			String lName, String cellPhone, String homePhone, String workPhone, String email, String company,
			String address, String city, String state, String zipCode) throws SQLException {
		log("editContact method for Prospector Pro starting now.");

		Connection root = initialize(dataPass, path);
		String editStatement = "UPDATE Contact SET Fname=?, Middle=?, Lname=?, OtherPhone=?, "
				+ "HomPhone=?, WorkPhone=?, Email=?, Company=?, Address=?, City=?, State=?, "
				+ "ZipCode=? WHERE CustId=?";
		PreparedStatement edit = root.prepareStatement(editStatement);
		edit.setString(1, fName);
		edit.setString(2, middle);
		edit.setString(3, lName);
		edit.setString(4, cellPhone);
		edit.setString(5, homePhone);
		edit.setString(6, workPhone);
		edit.setString(7, email);
		edit.setString(8, company);
		edit.setString(9, address);
		edit.setString(10, city);
		edit.setString(11, state);
		edit.setString(12, zipCode);
		edit.setString(13, customerId);

		int rowsUpdated = edit.executeUpdate();

		root.close();

		return rowsUpdated;
	}

	/**
	 * this method will update the notes field in a contact, and is usually
	 * executed when someone edits the notes from the quick dialog in the
	 * contact screen
	 * 
	 * @param path
	 * @param dataPass
	 * @param customerId
	 * @param notes
	 * @return the number of rows updated
	 * @throws SQLException
	 */
	public static int editContactNotes(String path, String dataPass, String customerId, String notes)
			throws SQLException {
		log("editContactNotes method starting for Prospector Pro now.");

		Connection root = initialize(dataPass, path);
		String editNotes = "UPDATE Contact SET Notes=? WHERE CustId=?";
		PreparedStatement editNotesStatement = root.prepareStatement(editNotes, ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);

		editNotesStatement.setString(1, notes);
		editNotesStatement.setString(2, customerId);

		int rowsUpdated = editNotesStatement.executeUpdate();

		root.close();

		return rowsUpdated;
	}

	public static int updateNotesTable(String path, String databasePass, String noteIdIncrement, String originalNoteId,
			String entry) throws SQLException {
		log("updateNotesTable method starting now.");

		Connection root = initialize(databasePass, path);

		String inputNotes = "INSERT INTO Notes (NoteID, OrigNoteID, Memo) VALUES (?, ?, ?)";

		PreparedStatement updateNotes = root.prepareStatement(inputNotes);

		updateNotes.setString(1, noteIdIncrement);
		updateNotes.setString(2, originalNoteId);
		updateNotes.setString(3, entry);

		int rowsUpdated = updateNotes.executeUpdate();

		root.close();

		return rowsUpdated;
	}

	public static int updateSpecificActivityDetails(String path, String databasePass, String aspect, String entry,
			String activityId) throws SQLException {
		log("update specific activity type method running, setting " + aspect + " to " + entry);

		Connection root = initialize(databasePass, path);

		String updateNoteIDSql = "UPDATE Activities SET " + aspect + "=? WHERE ActivityID=?";

		PreparedStatement updateNoteID = root.prepareStatement(updateNoteIDSql);

		updateNoteID.setString(1, entry);
		updateNoteID.setString(2, activityId);

		int rowsUpdated = updateNoteID.executeUpdate();

		log(rowsUpdated + " row(s) were updated.");

		root.close();

		return rowsUpdated;
	}

	public static int addNewActivity(String path, String databasePass, String activityId, String customerId,
			String salesId, String scheduledDate, String scheduledTime, String activityType, String noteId,
			String fupref, String phone, String email, String location, String docPath, String hide, String eventId,
			String eventOwner) throws SQLException {
		log("New activity method started for contact " + customerId + '.');

		Connection root = initialize(databasePass, path);

		String values = "INSERT INTO Activities(ActivityID, CustId, SlsId, SchedDate, ActivityType, NoteID,"
				+ " FupRef, Phone, EMail, Location, DocPath, Hide, GoogleEventId, GoogleEventOwner) VALUES"
				+ "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		PreparedStatement newActivity = root.prepareStatement(values);

		newActivity.setString(1, activityId);
		newActivity.setString(2, customerId);
		newActivity.setString(3, salesId);
		newActivity.setString(4, scheduledDate.trim() + ' ' + scheduledTime.trim() + ":00.000");
		newActivity.setString(5, activityType);
		newActivity.setString(6, noteId);
		newActivity.setString(7, fupref);
		newActivity.setString(8, phone);
		newActivity.setString(9, email);
		newActivity.setString(10, location);
		newActivity.setString(11, docPath);
		newActivity.setString(12, hide);
		newActivity.setString(13, eventId);
		newActivity.setString(14, eventOwner);

		int rowsUpdated = newActivity.executeUpdate();

		log(rowsUpdated + " row(s) were updated.");

		root.close();

		return rowsUpdated;
	}

	public static int addNewContact(String databasePass, String path, String custId, int locId, int slsId,
			String company, String fname, String lname, String middle, String address, String city, String state,
			String zipCode, String country, String workPhone, String homPhone, String fax, String otherPhone,
			String webAddress, String leadSource, String style, String title, String classType, String dearName,
			String email, String industry, String fupref, String fUpExpiration, String notes, String lastCallInfo,
			String contactDate, String tceCustId, String tceStatus, String tceSoldDate, String yearEnd,
			String importField1, String importField2, String importField3, String importField4, String importField5,
			String importField6, String importField7, String importField8, String importField9, String importField10,
			String otherPhone1Name, String otherPhone1, String otherPhone2Name, String otherPhone2,
			String otherPhone3Name, String otherPhone3, String udt_text1, String udt_text2, String udt_text3,
			String udt_text4, String udt_text5, String udd_text1, String udd_text2, String udd_text3, String udd_text4,
			String udd_text5, String weblinkID, String origDate, String formLoc1, String formLoc2, String formLoc3,
			String formLoc4, String formLoc5, String formLoc6, String formLoc7, String formLoc8, String formLoc9,
			String formLoc10, String ph_Street, String ph_City, String ph_State, String ph_ZipCode, int score)
					throws SQLException {

		log("new contact method reached, giant PreparedStatement now compiling.");

		Connection root = initialize(databasePass, path);

		String insertContactSql = "INSERT INTO Contact(CustId, LocId, SlsId, "
				+ "Company, Fname, Lname, Middle, Address, City, State, ZipCode, "
				+ "Country, WorkPhone, HomPhone, Fax, OtherPhone, WebAddress, "
				+ "LeadSource, Style, Title, Class, DearName, Email, Industry, "
				+ "fupref, FupExpiration, Notes, LastCallInfo, ContactDate, TCECustId, "
				+ "TCEStatus, TCESoldDate, YearEnd, ImportField1, ImportField2, ImportField3, "
				+ "ImportField4, ImportField5, ImportField6, ImportField7, ImportField8, "
				+ "ImportField9, ImportField10, OtherPhone1Name, OtherPhone1, OtherPhone2Name, "
				+ "OtherPhone2, OtherPhone3Name, OtherPhone3, UDT_Text1, UDT_Text2, UDT_Text3, "
				+ "UDT_Text4, UDT_Text5, UDD_Text1, UDD_Text2, UDD_Text3, UDD_Text4, UDD_Text5, "
				+ "WeblinkID, OrigDate, FormLoc1, FormLoc2, FormLoc3, FormLoc4, FormLoc5, "
				+ "FormLoc6, FormLoc7, FormLoc8, FormLoc9, FormLoc10, Ph_Street, Ph_City, "
				+ "Ph_State, Ph_ZipCode, Score) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
				+ "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
				+ "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
				+ "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		PreparedStatement newContact = root.prepareStatement(insertContactSql);

		newContact.setString(1, custId);
		newContact.setInt(2, locId);
		newContact.setInt(3, slsId);
		newContact.setString(4, company);
		newContact.setString(5, fname);
		newContact.setString(6, lname);
		newContact.setString(7, middle);
		newContact.setString(8, address);
		newContact.setString(9, city);
		newContact.setString(10, state);
		newContact.setString(11, zipCode);
		newContact.setString(12, country);
		newContact.setString(13, workPhone);
		newContact.setString(14, homPhone);
		newContact.setString(15, fax);
		newContact.setString(16, otherPhone);
		newContact.setString(17, webAddress);
		newContact.setString(18, leadSource);
		newContact.setString(19, style);
		newContact.setString(20, title);
		newContact.setString(21, classType);
		newContact.setString(22, dearName);
		newContact.setString(23, email);
		newContact.setString(24, industry);
		newContact.setString(25, fupref);
		newContact.setString(26, fUpExpiration);
		newContact.setString(27, notes);
		newContact.setString(28, lastCallInfo);
		newContact.setString(29, contactDate);
		newContact.setString(30, tceCustId);
		newContact.setString(31, tceStatus);
		newContact.setString(32, tceSoldDate);
		newContact.setString(33, yearEnd);
		newContact.setString(34, importField1);
		newContact.setString(35, importField2);
		newContact.setString(36, importField3);
		newContact.setString(37, importField4);
		newContact.setString(38, importField5);
		newContact.setString(39, importField6);
		newContact.setString(40, importField7);
		newContact.setString(41, importField8);
		newContact.setString(42, importField9);
		newContact.setString(43, importField10);
		newContact.setString(44, otherPhone1Name);
		newContact.setString(45, otherPhone1);
		newContact.setString(46, otherPhone2Name);
		newContact.setString(47, otherPhone2);
		newContact.setString(48, otherPhone3Name);
		newContact.setString(49, otherPhone3);
		newContact.setString(50, udt_text1);
		newContact.setString(51, udt_text2);
		newContact.setString(52, udt_text3);
		newContact.setString(53, udt_text4);
		newContact.setString(54, udt_text5);
		newContact.setString(55, udd_text1);
		newContact.setString(56, udd_text2);
		newContact.setString(57, udd_text3);
		newContact.setString(58, udd_text4);
		newContact.setString(59, udd_text5);
		newContact.setString(60, weblinkID);
		newContact.setString(61, origDate);
		newContact.setString(62, formLoc1);
		newContact.setString(63, formLoc2);
		newContact.setString(64, formLoc3);
		newContact.setString(65, formLoc4);
		newContact.setString(66, formLoc5);
		newContact.setString(67, formLoc6);
		newContact.setString(68, formLoc7);
		newContact.setString(69, formLoc8);
		newContact.setString(70, formLoc9);
		newContact.setString(71, formLoc10);
		newContact.setString(72, ph_Street);
		newContact.setString(73, ph_City);
		newContact.setString(74, ph_State);
		newContact.setString(75, ph_ZipCode);
		newContact.setInt(76, score);

		int rowsUpdated = newContact.executeUpdate();

		root.close();

		return rowsUpdated;
	}

	/*
	 * 
	 * 
	 * 
	 * ****************BEGIN INVENTORY APPLICATION METHODS*********************
	 * 
	 * 
	 * 
	 * 
	 * 
	 */

	/**
	 * Generates the user information for use in the Inventory application.
	 * 
	 * @param path
	 * @param dataPass
	 * @param username
	 * @return the ResultSet of user information
	 * @throws SQLException
	 */
	public static ResultSet authenticateInventory(String path, String username) throws SQLException {
		log("authenticateInventory method starting for Inventory.");

		Connection root = initialize("", path);

		String authenticateSql = "SELECT * FROM UnitSearchRemoteClientInfo WHERE UserName=?";
		PreparedStatement authenticate = root.prepareStatement(authenticateSql, ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);
		authenticate.setString(1, username);

		ResultSet userInfo = authenticate.executeQuery();

		root.close();

		return userInfo;
	}

	/*
	 * 
	 * 
	 * 
	 * 
	 * 
	 * ****************BEGIN TIMECARD APPLICATION METHODS*****************
	 * 
	 * 
	 * 
	 * 
	 * 
	 */

	/**
	 * Authenticates and gets information about the user at the login for
	 * Timecard.
	 * 
	 * @param path
	 * @param username
	 * @return ResultSet containing user information
	 * @throws SQLException
	 */
	public static ResultSet authenticateTimecard(String path, String username) throws SQLException {
		log("authenticateTimecard method for Timecard starting.");

		Connection root = initialize("", path);

		String authenticateSql = "SELECT * FROM TimeCardRemoteClientInfo WHERE UserName=?";
		PreparedStatement authenticate = root.prepareStatement(authenticateSql);
		authenticate.setString(1, username);

		ResultSet userInfo = authenticate.executeQuery();

		root.close();

		return userInfo;
	}

	/*
	 * 
	 * 
	 * 
	 * 
	 * ****************BEGIN STANDARD APPLICATION METHODS**********************
	 * 
	 * 
	 * 
	 * 
	 * 
	 */

	/**
	 * Shows the total number of databases on this machine, and moves the cursor
	 * in the resultSet back to its initial position before the first row.
	 * 
	 * @param input
	 * @return
	 * @throws SQLException
	 */
	public static int getRowCount(ResultSet input) throws SQLException {
		log("getRowCount method starting.");
		boolean moved = input.last();
		if (moved) {
			int size = input.getRow();
			input.beforeFirst();
			boolean reset = input.isBeforeFirst();
			if (reset) {
				return size;
			} else {
				return -2;
			}
		}
		return -1;
	}

	/*
	 * 
	 * 
	 * 
	 * 
	 * MAIN SERVER DATABASE METHODS
	 * 
	 * 
	 * 
	 * 
	 * 
	 */

	/**
	 * This loads all Prospector Pro installations into memory for quick access
	 * by the server. This is run twice at startup, and once every 30 minutes
	 * (1800000 milliseconds) while the application is running.
	 */
	public static void initializeAllDatabases() {
		if (!Server4.isInitializing()) {
			Server4.setInitializing(true);
			log("Please wait, initializing data...");

			// get employee Info from RMAPPServer database
			try {
				ResultSet employeeInfo = DatabaseTools.findAllDatabases(Server4.getRMAppServerPath());

				int numberOfDatabases = DatabaseTools.getRowCount(employeeInfo);

				if (numberOfDatabases != 1) {
					log("We found " + numberOfDatabases + " databases on this machine.");
				} else {
					log("We found 1 database on this machine.");
				}

				while (employeeInfo.next()) {
					DatabaseTools.initialize(Server4.getDatabasePass(), employeeInfo.getString("FilePath"));
				}

				/*
				 * should only reach when the data has been initialized
				 */
				log("Initialization of data has completed.");
				Server4.incrementCounter();

			} catch (SQLException err) {
				err.printStackTrace();
				log("Error in " + Server4.getRMAppServerPath() + ".\n\nThe details are: " + err.toString()
						+ "\n\n The program will now exit.");

				Server4.setCounterToMinus1();

				// System.exit(1);
			}

			Server4.setInitializing(false);

		} else {
			log("Databases were already being initialized, skipping this command to re-initialize them.");
		}
	}

	/**
	 * prints a message to the console with the date, time and Inventory string
	 * appended to it
	 * 
	 * @param message
	 */
	private static void log(String message) {
		Write.writeLine(APP_NAME, message);
	}
}