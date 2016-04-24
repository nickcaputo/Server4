package app_threads;
/**
 * @author Nicholas Caputo, (847) 630 7370
 * 
 * this class contains all the possible commands that could be requested by the Prospector Pro Mobile 
 * app, and a new instance of it is created and destroyed for every single command coming through the
 * main server socket
 */

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Scanner;
import java.time.LocalDateTime;

import main_threads.Server4;
import tools.SavedValues;
import tools.DatabaseTools;
import tools.StrMods;
import tools.Write;

/**
 * 
 * @author Nicholas Caputo, npocaputo@GMail.com, (847) 630 7370
 *
 */
public class ProspectorPro extends Thread {

	private static final String APP_NAME = "Prospector Pro";

	private static final String SEPARATOR = SavedValues.getContentSeparator();
	private static final String ITEM_SEP = SavedValues.getItemSeparator();

	private Socket socket;
	private PrintWriter outToClient;

	private String valuesForEntry;
	private String[] requestFromPhone;
	private StringBuilder resultBuilder;
	private long counter;
	private short stepNumber = 0;

	/*
	 * This number controls how many contacts to get at application login
	 */
	private int contactsToGet = 50;
	private int contactsToGetPerRequest = 50;

	// for debug purposes
	private long startTime;

	/**
	 * constructor gives the class the commands for data from the phone, and the
	 * number represented by the counter in the main Server class
	 * 
	 * @param valuesForEntry
	 * @param counter
	 */
	public ProspectorPro(String valuesForEntry, long counter) {
		startTime = System.nanoTime();

		socket = Server4.getSocket(counter);
		this.valuesForEntry = valuesForEntry;
		this.counter = counter;
		log("Starting Prospector Pro thread now.");
		connection.start();
	}

	/**
	 * the worker thread for the class, allows everything to be done
	 * asynchronously. It is the main block of code that delegates which methods
	 * should be called for this command.
	 */
	Thread connection = new Thread() {
		public void run() {
			log("Prospector Pro thread started.");

			resultBuilder = new StringBuilder();

			try {
				outToClient = new PrintWriter(socket.getOutputStream());

				/*
				 * splits string sent over the connection into values and reads
				 * the first values to get commands
				 */
				String delimiter = "\\" + SEPARATOR;
				requestFromPhone = valuesForEntry.split(delimiter);

				String commandSelection = requestFromPhone[0];
				String user = requestFromPhone[1];
				String dataPass = requestFromPhone[2];
				String idGivenFromPhone = requestFromPhone[3];

				String[] employeeInfoSet = getEmployeeInfo(user);
				// slsId is employeeInfo[0]
				// locId is employeeInfo[1]
				String pProDatabasePath = employeeInfoSet[2];
				// fupref is employeeInfo[3]
				// defaultTimeFrame is employeeInfo[4]
				// custClass is employeeInfo[5]

				if (employeeInfoSet != null) {
					performPProAction(pProDatabasePath, employeeInfoSet, dataPass, idGivenFromPhone, user,
							commandSelection);
				}

			} catch (IOException err) {
				err.printStackTrace();
			} finally {
				sendDataBack();
				shutDownResources();
			}
		}

		/*
		 * 
		 * 
		 * 
		 * ***************Main delegate methods****************
		 * 
		 * 
		 * 
		 * 
		 */

		/**
		 * gets the employee location ID and sales Id for this person who is
		 * logging into the app, uses this information in case they are adding a
		 * note, new contact, or editing any content
		 * 
		 * @param user
		 */
		private String[] getEmployeeInfo(String user) {
			// get employee Info from RMAPPServer database
			try {
				log("Getting employee information (getEmployeeInfo method starting)");

				/*
				 * stores the information about the employee you are selecting
				 */
				ResultSet employeeInfo = DatabaseTools.findDatabase(user, Server4.getRMAppServerPath());
				log("Employee information gathered");
				if (employeeInfo.next()) {
					String slsId = employeeInfo.getString("SlsId");
					String locId = employeeInfo.getString("LocId");
					String pProDatabasePath = employeeInfo.getString("FilePath");
					String fupref = employeeInfo.getString("DefaultFollowUp");
					String defaultTimeFrame = employeeInfo.getString("DefaultTimeFrame");
					String custClass = employeeInfo.getString("DefaultClass");

					String[] employeeInfoSet = new String[] { slsId, locId, pProDatabasePath, fupref, defaultTimeFrame,
							custClass };

					resultBuilder.append("Employee Info populated. ");

					return employeeInfoSet;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}

			return null;
		}

		/**
		 * Determines which series of actions have to be taken for a request
		 * with Prospector Pro.
		 * 
		 * @param pProDbPath
		 * @param employeeInfoSet
		 * @param dbPass
		 * @param id
		 * @param user
		 * @param command
		 */
		private void performPProAction(String pProDbPath, String[] employeeInfoSet, String dbPass, String id,
				String user, String command) {
			log("performPProAction method starting.");

			/*
			 * checks to make sure the request is not blank
			 */

			log("Checking if there is content in the request.");
			boolean thereIsContentInTheRequest = false;
			for (int positionInData = 4; positionInData < requestFromPhone.length; positionInData++) {
				if (!requestFromPhone[positionInData].trim().isEmpty()) {
					thereIsContentInTheRequest = true;
				}
			}

			switch (command) {

			case "newContact":

				addNewItemToDb(thereIsContentInTheRequest, employeeInfoSet, id, dbPass, pProDbPath, command);
				break;

			case "retrieveContact":

				getSpecificItem(id, command, dbPass, pProDbPath);
				break;

			case "qs":
			case "qsMore":

				contactQs(dbPass, pProDbPath, id, command);
				break;

			case "search":

				search(dbPass, pProDbPath, command);
				break;

			case "editContact":

				editContact(dbPass, pProDbPath, id, command, thereIsContentInTheRequest);
				break;

			case "simpleUserActivityList":

				getSimpleActivityList(dbPass, pProDbPath, id);
				break;

			case "simpleDateSearch":

				simpleDateSearch(dbPass, pProDbPath);
				break;

			case "simpleDateRange":

				simpleDateRange(dbPass, pProDbPath);
				break;

			case "editActivitySimple":

				editSimpleActivityNotes(dbPass, pProDbPath, id);
				break;

			case "editActivityDateSimple":

				editSimpleActivityDate(dbPass, pProDbPath, id);
				break;
			}
		}

		/**
		 * Prints out the string of data to send back to the client, and closes
		 * this thread for Prospector Pro.
		 */
		private void sendDataBack() {
			String resultToSend = resultBuilder.toString();

			outToClient.println(resultToSend);
			log("Results sent to client: " + resultToSend);
		}

		/**
		 * Destroys the thread and releases the resources to the system. This
		 * method does not return normally.
		 */
		private void shutDownResources() {
			outToClient.close();

			/*
			 * closes the socket used for this thread
			 */
			try {
				socket.close();
			} catch (IOException err) {
				err.printStackTrace();
			}

			long futureTime = System.nanoTime();

			long nanos = futureTime - startTime;
			long millis = nanos / 1000000;
			log("This operation took " + nanos + " nanoseconds (" + millis + " ms).");

			/*
			 * removes this from the main HashMap thereby self-destroying the
			 * thread. Its resources are then re-used.
			 */
			Server4.removeThread("ProspectorPro", counter);
		}

		/*
		 * 
		 * 
		 * 
		 * **************Head worker methods********************
		 * 
		 * These are called from the performPProAction method.
		 * 
		 * 
		 */

		/**
		 * Begins the process to add an item (contact or activity) into the main
		 * database.
		 * 
		 * @param thereIsContentInTheRequest
		 * @param employeeInfoSet
		 * @param id
		 * @param dbPass
		 * @param pProDbPath
		 * @param command
		 */
		private void addNewItemToDb(boolean thereIsContentInTheRequest, String[] employeeInfoSet, String id,
				String dbPass, String pProDbPath, String command) {
			if (thereIsContentInTheRequest) {

				String newId;

				if (command.equals("newActivity")) {
					newId = buildSQLQuery_insertActivity(employeeInfoSet, id, dbPass);
				} else { // if
							// (commandSelection.equals("insertContact"))
							// {
					newId = buildSQLQuery_insertContact(employeeInfoSet, dbPass);
				}

				log("Performing add function");

				resultBuilder.append("Record created. ");

				/*
				 * shows the activity that was just created
				 */
				if (command.equals("newActivity")) {

					try {
						ResultSet activityJustCreated = DatabaseTools.getActivity(pProDbPath, dbPass, newId);
						serializeActivity(activityJustCreated, command, dbPass, pProDbPath);
					} catch (SQLException err) {
						err.printStackTrace();
					}

				}
			} else {
				resultBuilder.append("Empty record not created. ");
			}
		}

		/**
		 * Begins the required actions to perform a quick search on the user's
		 * contacts.
		 * 
		 * @param dbPass
		 * @param pProDbPath
		 * @param id
		 * @param command
		 */
		private void contactQs(String dbPass, String pProDbPath, String id, String command) {
			log("Performing contact quick search");
			ResultSet quickSearchResult;
			boolean quickSearchingMoreContacts = false;

			/*
			 * in a qsMore command, the idGivenFromPhone is an integer detailing
			 * how many results to get
			 */
			if (command.equals("qsMore")) {
				contactsToGet = Integer.parseInt(id);
				log("Searching contacts up to " + contactsToGet);
				quickSearchingMoreContacts = true;
			}

			log("Searching the top " + contactsToGet + " contacts.");

			try {
				quickSearchResult = DatabaseTools.quickSearchContacts(dbPass, pProDbPath, contactsToGet);

				if (quickSearchResult.next()) {
					resultBuilder.append("Starting collection of contact information. ");
					quickSearchResult.previous();

					serializeContact(quickSearchResult, command, "", quickSearchingMoreContacts);

				} else {
					resultBuilder.append("Try recieving the Quick Search again. ");
				}

			} catch (SQLException err) {
				err.printStackTrace();
			}
		}

		/**
		 * Begins the methods to search through a set of people in the database.
		 * 
		 * @param dbPass
		 * @param pProDbPath
		 * @param command
		 */
		private void search(String dbPass, String pProDbPath, String command) {
			log("Performing search function.");
			ResultSet searchResultContacts;

			/*
			 * making sure the result is something usable
			 */
			if (!(requestFromPhone[4] == null)) {

				/*
				 * making sure the request is at least three characters before
				 * searching
				 */
				if (requestFromPhone[4].length() >= 3) {

					// String searchQuery = requestFromPhone[4].replace(' ',
					// '%');

					log("Performing search with criteria " + requestFromPhone[4]);

					try {

						searchResultContacts = DatabaseTools.searchContacts(dbPass, pProDbPath, requestFromPhone[4]);

						if (searchResultContacts.next()) {

							searchResultContacts.previous();
							resultBuilder.append("Starting collection of contact information. ");

							/*
							 * shows contacts
							 */
							serializeContact(searchResultContacts, command, "", false);

						} else {
							resultBuilder.append("There was no result in the database for that request. ");
						}

					} catch (SQLException err) {
						err.printStackTrace();
					}
				}

			} else {
				resultBuilder.append("The request to the server was null. ");
			}
		}

		/**
		 * Begins the process to edit a contact in the database.
		 * 
		 * @param dbPass
		 * @param pProDbPath
		 * @param id
		 * @param command
		 * @param thereIsContentInTheRequest
		 */
		private void editContact(String dbPass, String pProDbPath, String id, String command,
				boolean thereIsContentInTheRequest) {
			log("Performing edit contact functions.");

			if (thereIsContentInTheRequest) {
				/*
				 * updates the selected contact and returns it to the device
				 */

				id = buildSQLQuery_editContact(id, dbPass, pProDbPath);
				log("Edited contact with Customer ID " + id);

				/*
				 * returns the updated contact to the device
				 */
				getSpecificItem(id, command, dbPass, pProDbPath);

			} else {
				resultBuilder.append("Update was not requested. ");
			}
		}

		private void getSimpleActivityList(String dbPass, String pProDbPath, String id) {
			log("RECIEVED COMMAND TO GET SIMPLE ACTIVITIES");
			ResultSet results = null;
			String name = null;

			try {
				results = DatabaseTools.searchActivities_noQueries(dbPass, pProDbPath, id);
				name = DatabaseTools.getCustomerNameFromId(dbPass, pProDbPath, id);
			} catch (SQLException err) {
				err.printStackTrace();
			}

			if (results != null) {
				serializeSimpleActivityList(results, name, dbPass, pProDbPath);
			}
		}

		/**
		 * This is the delegate method for searching all activities on a single
		 * date
		 * 
		 * @param dbPass
		 * @param pProDbPath
		 */
		private void simpleDateSearch(String dbPass, String pProDbPath) {
			log("RETRIEVED COMMAND TO SEARCH DATE");
			ResultSet search = null;
			
			String date = requestFromPhone[3];

			try {
				search = DatabaseTools.searchSingleDate_noQueries(dbPass, pProDbPath, date);
			} catch (SQLException err) {
				err.printStackTrace();
			}

			if (search != null) {
				serializeSimpleActivityList(search, null, dbPass, pProDbPath);
			}
		}

		/**
		 * This is the delegate method for searching all activities between a
		 * range of dates
		 * 
		 * @param dbPass
		 * @param pProDbPath
		 */
		private void simpleDateRange(String dbPass, String pProDbPath) {
			log("RETRIEVED COMMAND TO SEARCH DATE RANGE");
			ResultSet dateRange = null;
			
			String fromDate = requestFromPhone[3];
			String toDate = requestFromPhone[4];

			try {
				dateRange = DatabaseTools.searchDateRange_noQueries(dbPass, pProDbPath, fromDate,
						toDate);
			} catch (SQLException err) {
				err.printStackTrace();
			}

			if (dateRange != null) {
				serializeSimpleActivityList(dateRange, null, dbPass, pProDbPath);
			}
		}

		private void editSimpleActivityNotes(String dbPass, String pProDbPath, String id) {
			log("RETRIEVED COMMAND TO EDIT SIMPLE ACTIVITY NOTES");

			for (int i = 0; i < requestFromPhone.length; i++) {
				String item = requestFromPhone[i];
				log("Item " + i + ": " + item);
			}

			if (requestFromPhone.length >= 7) {
				String activityType = requestFromPhone[4];
				String custId = requestFromPhone[5];
				String note = requestFromPhone[6];

				try {
					ResultSet set = null;
					int rowsUpdated = DatabaseTools.updateActivityNotes_noQueries(dbPass, pProDbPath, id, activityType,
							note);
					set = DatabaseTools.getSimpleActivity_noQueries(dbPass, pProDbPath, activityType, id);
					String name = DatabaseTools.getCustomerNameFromId(dbPass, pProDbPath, custId);

					log("Customer name is " + name);
					log("ROWS UPDATED: " + rowsUpdated);

					log("SET IS ACTIVE " + (set != null));
					if (set != null) {
						serializeSimpleActivityList(set, name, dbPass, pProDbPath);
					}

				} catch (SQLException err) {
					err.printStackTrace();
				}
			} else {
				log("Not enough data was sent through, the request was " + requestFromPhone.length + " items long.");
			}
		}
		
		private void editSimpleActivityDate(String dbPass, String pProDbPath, String id) {
			for (int i = 0; i < requestFromPhone.length; i++) {
				log("Item " + i + ": " + requestFromPhone[i]);
			}

			if (requestFromPhone.length >= 7) {
				String activityType = requestFromPhone[4];
				String custId = requestFromPhone[5];
				boolean editingCompleted = Boolean.parseBoolean(requestFromPhone[6]);
				String dateTime = requestFromPhone[7];

				try {
					int rowsUpdated = DatabaseTools.changeActivityDate_noQueries(dbPass, pProDbPath, activityType,
							id, editingCompleted, dateTime);
					log("Updated " + rowsUpdated + " rows in the table.");

					ResultSet set = DatabaseTools.getSimpleActivity_noQueries(dbPass, pProDbPath, activityType, id);
					String name = DatabaseTools.getCustomerNameFromId(dbPass, pProDbPath, custId);

					log("Customer name is " + name);
					log("ROWS UPDATED: " + rowsUpdated);

					log("SET IS ACTIVE " + (set != null));
					if (set != null) {
						serializeSimpleActivityList(set, name, dbPass, pProDbPath);
					}

				} catch (SQLException err) {
					err.printStackTrace();
				}
			}
		}

		/*
		 * 
		 * 
		 * 
		 * 
		 * **************Worker methods that are re-used many times in the head
		 * worker methods.***********
		 * 
		 * 
		 * 
		 * 
		 * 
		 */

		/**
		 * Here the resultSet from the previous query is serialized into a
		 * string which is sent over to the app
		 * 
		 * @param contactsToShow
		 * @param notesToSend
		 */
		private void serializeContact(ResultSet contactsToShow, String commandSelection, String notesToSend,
				boolean quickSearchingMoreContacts) {

			log("Now starting the showContact method.");

			/*
			 * the amount of items to get at one time should we be selecting the
			 * "Get More Contacts" feature
			 */
			int limit = contactsToGet - contactsToGetPerRequest;

			try {
				StringBuilder contactItemsBuilder = new StringBuilder();
				int contactNumber = 0;

				while (contactsToShow.next()) {

					if ((quickSearchingMoreContacts && contactNumber > limit)
							|| (!quickSearchingMoreContacts && contactNumber != 0)) {
						contactItemsBuilder.append(ITEM_SEP);
					}

					if (!quickSearchingMoreContacts || (quickSearchingMoreContacts && contactNumber >= limit)) {

						contactItemsBuilder.append(SEPARATOR).append("Contact ").append(contactNumber).append(" ")
								.append(SEPARATOR).append("custid:" + contactsToShow.getString("CustId"))
								.append(SEPARATOR).append("fName:" + contactsToShow.getString("Fname"))
								.append(SEPARATOR).append("lName:" + contactsToShow.getString("Lname"))
								.append(SEPARATOR).append("email:" + contactsToShow.getString("Email"))
								.append(SEPARATOR).append("cellphone:" + contactsToShow.getString("OtherPhone"))
								.append(SEPARATOR).append("lookingfor:" + contactsToShow.getString("Style"))
								.append(SEPARATOR).append("timeframe:" + contactsToShow.getString("Title"))
								.append(SEPARATOR).append("mi:" + contactsToShow.getString("Middle")).append(SEPARATOR)
								.append("class:" + contactsToShow.getString("Class")).append(SEPARATOR)
								.append("score:" + contactsToShow.getString("Score")).append(SEPARATOR)
								.append("company:" + contactsToShow.getString("Company")).append(SEPARATOR)
								.append("notes:").append(notesToSend);

						if (!(commandSelection.equals("qs") || commandSelection.equals("qsMore")
								|| commandSelection.equals("search"))) {
							contactItemsBuilder.append(SEPARATOR).append("address:")
									.append(contactsToShow.getString("Address") + SEPARATOR).append("city:")
									.append(contactsToShow.getString("City")).append(SEPARATOR)
									.append("state:" + contactsToShow.getString("State")).append(SEPARATOR)
									.append("zipCode:" + contactsToShow.getString("ZipCode")).append(SEPARATOR)
									.append("budget:" + contactsToShow.getString("Country")).append(SEPARATOR)
									.append("niche:" + contactsToShow.getString("Industry")).append(SEPARATOR)
									.append("homephone:" + contactsToShow.getString("HomPhone")).append(SEPARATOR)
									.append("workphone:" + contactsToShow.getString("WorkPhone")).append(SEPARATOR);
						}

					}

					contactNumber++;
				}

				String contactItems = contactItemsBuilder.toString();
				resultBuilder.append(contactItems);
			} catch (SQLException err) {
				err.printStackTrace();
			}
		}

		/**
		 * this takes the ResultSet from the SQL command asking for a range of
		 * activities, and creates a string to send back to the application
		 * which can be used to display content on the phone screen
		 * 
		 * @param activitiesList
		 */
		private void serializeActivity(ResultSet activitiesList, String commandSelection, String dataPass,
				String pProDatabasePath) {
			try {
				StringBuilder searchItemsBuilder = new StringBuilder();
				String activityId;
				int activityNumber = 0;

				if (activitiesList != null) {
					while (activitiesList.next()) {
						activityId = activitiesList.getString("ActivityID");

						if (activityNumber != 0)
							searchItemsBuilder.append(ITEM_SEP);

						searchItemsBuilder.append(SEPARATOR).append("Activity ").append(activityNumber)
								.append(SEPARATOR).append("activityId:" + activityId).append(SEPARATOR)
								.append("custId:").append(activitiesList.getString("CustId"))
								.append(SEPARATOR + "slsId:").append(activitiesList.getString("SlsId"))
								.append(SEPARATOR).append("scheduled:" + activitiesList.getString("SchedDate"))
								.append(SEPARATOR).append("completed:" + activitiesList.getString("CompDate"))
								.append(SEPARATOR).append("activityType:" + activitiesList.getString("ActivityType"))
								.append(SEPARATOR).append("noteId:" + activitiesList.getString("NoteID"))
								.append(SEPARATOR).append("fupref:" + activitiesList.getString("FupRef"))
								.append(SEPARATOR).append("phone:" + activitiesList.getString("Phone"))
								.append(SEPARATOR).append("location:" + activitiesList.getString("Location"))
								.append(SEPARATOR).append("email:" + activitiesList.getString("Email"))
								.append(SEPARATOR).append("docPath:" + activitiesList.getString("DocPath"))
								.append(SEPARATOR).append("hide:" + activitiesList.getString("Hide")).append(SEPARATOR)
								.append("fName:" + activitiesList.getString("Fname")).append(SEPARATOR).append("mi:")
								.append(activitiesList.getString("Middle") + SEPARATOR).append("lName:")
								.append(activitiesList.getString("Lname")).append(SEPARATOR)
								.append("cellphone:" + activitiesList.getString("OtherPhone")).append(SEPARATOR)
								.append("homephone:" + activitiesList.getString("HomPhone")).append(SEPARATOR)
								.append("workphone:" + activitiesList.getString("WorkPhone")).append(SEPARATOR)
								.append("emailContact:" + activitiesList.getString("Email")).append(SEPARATOR)
								.append("addressContact:" + activitiesList.getString("Address")).append(SEPARATOR)
								.append("company:" + activitiesList.getString("Company")).append(SEPARATOR)
								.append("eventId:" + activitiesList.getString("GoogleEventId")).append(SEPARATOR)
								.append("eventOwner:" + activitiesList.getString("GoogleEventOwner")).append(SEPARATOR);

						activityNumber++;

						if (commandSelection.equals("retrieveActivity") || commandSelection.equals("newActivity")
								|| commandSelection.equals("editActivity")) {

							ResultSet retrieveFromNotes = DatabaseTools.getActivityNote(pProDatabasePath, dataPass,
									activityId);

							if (retrieveFromNotes.next()) {
								searchItemsBuilder.append(SEPARATOR).append("hasNotes:TRUE").append(SEPARATOR)
										.append("noteIdFromNotesTable:" + retrieveFromNotes.getString("NoteID"))
										.append(SEPARATOR)
										.append("origNoteId:" + retrieveFromNotes.getString("OrigNoteID"))
										.append(SEPARATOR).append("notes:" + retrieveFromNotes.getString("Memo"))
										.append(SEPARATOR)
										.append("dateCreated:" + retrieveFromNotes.getString("DateTime"))
										.append(SEPARATOR);

								log("Notes are: " + retrieveFromNotes.getString("Memo"));

							} else {
								searchItemsBuilder.append(SEPARATOR).append("hasNotes:FALSE").append(SEPARATOR);
							}
						}
					}
				}

				String searchItems = searchItemsBuilder.toString();
				resultBuilder.append(searchItems);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		private void serializeSimpleActivityList(ResultSet simpleList, String name, String dbPass, String dbPath) {
			HashMap<String, String> custIdNames = new HashMap<>();

			try {
				StringBuilder searchItemsBuilder = new StringBuilder();
				int activityNumber = 0;

				while (simpleList.next()) {
					String custId = StrMods.checkForValue(simpleList.getString("CustId"));
					String specificId = StrMods.checkForValue(simpleList.getString("SpecificId"));
					String slsId = StrMods.checkForValue(simpleList.getString("SlsId"));
					String essentials = StrMods.checkForValue(simpleList.getString("Essentials"));
					String scheduledDate = StrMods.checkForValue(simpleList.getString("ScheduledDate"));
					String completedDate = StrMods.checkForValue(simpleList.getString("CompletedDate"));
					String scheduledTime = StrMods.checkForValue(simpleList.getString("ScheduledTime"));
					String typeCorr = StrMods.checkForValue(simpleList.getString("TypeCorr"));
					String notes = StrMods.checkForValue(simpleList.getString("Notes"));
					String type = StrMods.checkForValue(simpleList.getString("Type"));

					if (notes != null) {
						log(notes);
						notes = notes.replace(System.lineSeparator(), SavedValues.getUTF8LineSep());
						// .replaceAll("^", SavedValues.getUTF8CaretSym());
					}

					if (activityNumber != 0) {
						searchItemsBuilder.append(ITEM_SEP);
					}

					searchItemsBuilder.append(SEPARATOR).append("Activity ").append(activityNumber).append(SEPARATOR)
							.append("custId:").append(custId).append(SEPARATOR).append("specificId:").append(specificId)
							.append(SEPARATOR).append("slsId:").append(slsId).append(SEPARATOR).append("essentials:")
							.append(essentials).append(SEPARATOR).append("scheduledDate:").append(scheduledDate)
							.append(SEPARATOR).append("completedDate:").append(completedDate).append(SEPARATOR)
							.append("scheduledTime:").append(scheduledTime).append(SEPARATOR).append("typeCorr:")
							.append(typeCorr).append(SEPARATOR).append("notes:").append(notes).append(SEPARATOR)
							.append("type:").append(type).append(SEPARATOR);

					if (name != null) {
						searchItemsBuilder.append("name:").append(name).append(SEPARATOR);
					} else {
						String nameLookup;
						if (custIdNames.get(custId) == null) {
							nameLookup = DatabaseTools.getCustomerNameFromId(dbPass, dbPath, custId);
							custIdNames.put(custId, nameLookup);
						} else {
							nameLookup = custIdNames.get(custId);
						}
						searchItemsBuilder.append("name:").append(nameLookup).append(SEPARATOR);
					}

					activityNumber++;
				}

				String searchItems = searchItemsBuilder.toString();
				resultBuilder.append(searchItems);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		/**
		 * retrieves the contact or activity with the respective ID given from
		 * the device, and adds its information to the string which is returned
		 * to the device
		 * 
		 * @param idGivenFromPhone
		 * @throws SQLException
		 */
		private void getSpecificItem(String idGivenFromPhone, String commandSelection, String dataPass,
				String pProDatabasePath) {
			log("Performing retrieve");

			try {
				// if (commandSelection.equals("retrieveActivity")) {
				// information = DatabaseTools.getActivity(pProDatabasePath,
				// dataPass, idGivenFromPhone);
				// } else {
				ResultSet information = DatabaseTools.getContact(pProDatabasePath, dataPass, idGivenFromPhone);
				// }

				if (information.next()) {

					/*
					 * this part of the code will read the content in the notes
					 * field, which may include line breaks from older versions
					 * of Prospector Pro. Then it will make them into one line
					 * of text so that they can be easily placed in the string
					 * to be sent back to the mobile application
					 */
					StringBuilder notesBuilder = new StringBuilder();
					if (information.getObject("Notes") != null) {
						Scanner lineScanner = new Scanner(information.getString("Notes"));
						while (lineScanner.hasNext()) {
							String nextLineNotes = lineScanner.nextLine().replace(System.lineSeparator(),
									SavedValues.getUTF8LineSep());
							notesBuilder.append(nextLineNotes).append(SEPARATOR);
						}
						lineScanner.close();
					}

					/*
					 * moves the result back so we can look at the row again
					 */
					information.previous();

					/*
					 * shows contact or activity
					 */
					if (commandSelection.equals("retrieveActivity")) {
						serializeActivity(information, commandSelection, dataPass, pProDatabasePath);
					} else {
						String notesToSend = notesBuilder.toString();
						serializeContact(information, commandSelection, notesToSend, false);
					}

				} else {
					resultBuilder.append("Try recieving the contact, contact to edit, or activity again. ");
				}

			} catch (SQLException err) {
				err.printStackTrace();
			}
		}

		/**
		 * creates a new contact in the database by using the information
		 * provided in the request, and automatically creating a new customerId
		 * 
		 * @return the array of the SQL statement and the customerId
		 */
		private String buildSQLQuery_insertContact(String[] employeeInfo, String dataPass) {
			String slsId = employeeInfo[0];
			String locId = employeeInfo[1];
			String pProDatabasePath = employeeInfo[2];
			String fupref = employeeInfo[3];
			String defaultTimeFrame = employeeInfo[4];
			String custClass = employeeInfo[5];

			/*
			 * Creates Customer ID first before doing anything
			 */
			String newCustomerId = null;
			try {
				newCustomerId = createUniqueId(locId, slsId, dataPass, pProDatabasePath);
			} catch (SQLException e) {
				e.printStackTrace();
			}

			/*
			 * declares variables to use in SQL Statement
			 */
			String fName = "";

			String mi = "";

			String lName = "";

			String cell = "";

			String home = "";

			String work = "";

			String company = "";

			String eMail = "";

			String address = "";

			String city = "";

			String state = "";

			String zipCode = "";

			String notes = "";

			/*
			 * populates variables from request
			 */
			for (int positionInData = 4; positionInData < requestFromPhone.length; positionInData++) {
				String dataTag = requestFromPhone[positionInData].substring(0,
						requestFromPhone[positionInData].indexOf(':'));
				String content = requestFromPhone[positionInData]
						.substring(requestFromPhone[positionInData].indexOf(':') + 1);
				switch (dataTag) {
				case "fName":
					fName = content;
					break;
				case "mi":
					mi = content;
					break;
				case "lName":
					lName = content;
					break;
				case "cell":
					cell = content;
					break;
				case "home":
					home = content;
					break;
				case "work":
					work = content;
					break;
				case "company":
					company = content;
					break;
				case "EMail":
					eMail = content;
					break;
				case "address":
					address = content;
					break;
				case "city":
					city = content;
					break;
				case "state":
					state = content;
					break;
				case "zipCode":
					zipCode = content;
					break;
				case "Notes":
					notes = "[Added_on(" + LocalDateTime.now().toString().replaceAll("[T]", " ") + ")] " + content;
					break;
				}
			}

			if (newCustomerId != null) {
				try {
					DatabaseTools.addNewContact(dataPass, pProDatabasePath, newCustomerId, 1, 101, company.trim(),
							fName.trim(), lName.trim(), mi.trim(), address.trim(), city.trim(), state.trim(),
							zipCode.trim(), "", work.trim(), home.trim(), "", cell.trim(), "", "", "", defaultTimeFrame,
							custClass, "", eMail.trim(), "", fupref, "9999-12-31 11:59:59.999", notes.trim(), "",
							"2015-05-12 00:00:00.000", "", "", "1976-05-25 00:00:00.000", "", "", "", "", "", "", "",
							"", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
							LocalDateTime.now().toString().replaceAll("[T]", " "), "", "", "", "", "", "", "", "", "",
							"", "", "", "", "", 100);
				} catch (SQLException err) {
					err.printStackTrace();
				}

				log("Creating new contact in database.");
			}

			return newCustomerId;
		}

		/**
		 * builds the SQL statement that is required to edit a contact in the
		 * database
		 * 
		 * @param idGivenFromPhone,
		 *            the CustomerId
		 * @return the modified customerId, if it is modified
		 * @throws SQLException
		 */
		private String buildSQLQuery_editContact(String idGivenFromPhone, String dataPass, String pProDatabasePath) {

			/*
			 * if there is an asterisk in the customerId, then the update is
			 * only for the notes and came from the notes dialog in the contact
			 * screen
			 */
			if (requestFromPhone[3].indexOf('*') == -1) {

				String fName = "";
				String middle = "";
				String lName = "";
				String cellPhone = "";
				String homePhone = "";
				String workPhone = "";
				String email = "";
				String company = "";
				String address = "";
				String city = "";
				String state = "";
				String zipCode = "";

				for (int index = 4; index < requestFromPhone.length; index++) {

					/*
					 * updates fields if something is entered in them, prevents
					 * ArrayIndexOutOfBoundsExceptions
					 */
					if (!requestFromPhone[index].trim().isEmpty()) {
						String partOne = requestFromPhone[index].substring(0,
								requestFromPhone[index].indexOf('\'') + 1);
						String partTwo = requestFromPhone[index].substring(requestFromPhone[index].indexOf('\'') + 1);
						switch (partOne) {
						case "Fname='":
							fName = partTwo;
							break;
						case "Middle='":
							middle = partTwo;
							break;
						case "Lname='":
							lName = partTwo;
							break;
						case "OtherPhone='":
							cellPhone = partTwo;
							break;
						case "HomPhone='":
							homePhone = partTwo;
							break;
						case "WorkPhone='":
							workPhone = partTwo;
							break;
						case "Email='":
							email = partTwo;
							break;
						case "Company='":
							company = partTwo;
							break;
						case "Address='":
							address = partTwo;
							break;
						case "City='":
							city = partTwo;
							break;
						case "State='":
							state = partTwo;
							break;
						case "ZipCode='":
							zipCode = partTwo;
							break;
						}
					}
				}

				/*
				 * EXECUTES UPDATE ON DATABASE
				 */
				try {

					int rowsUpdated = DatabaseTools.editContact(pProDatabasePath, dataPass, idGivenFromPhone, fName,
							middle, lName, cellPhone, homePhone, workPhone, email, company, address, city, state,
							zipCode);

					/*
					 * Checks to see if contact was updated
					 */
					if (rowsUpdated > 0) {
						resultBuilder.append("Record updated. ");
					}

				} catch (SQLException err) {
					err.printStackTrace();
				}

			} else {
				idGivenFromPhone = idGivenFromPhone.substring(1);
				String notes = requestFromPhone[4];

				/*
				 * EXECUTES UPDATE ON DATABASE, UPDATES NOTES FIELD
				 */
				try {
					DatabaseTools.editContactNotes(pProDatabasePath, dataPass, idGivenFromPhone, notes);
				} catch (SQLException err) {
					err.printStackTrace();
				}
			}

			return idGivenFromPhone;
		}

		/**
		 * creates a new activity in the database. 1) gets a new AutoNumber and
		 * creates a unique ActivityID from it 2) generates SQL Statement to use
		 * in creating activity
		 * 
		 * @return activity SQL statement and activityId
		 */
		private String buildSQLQuery_insertActivity(String[] employeeInfo, String idGivenFromPhone, String dataPass) {
			String slsId = employeeInfo[0];
			String locId = employeeInfo[1];
			String pProDatabasePath = employeeInfo[2];
			// fupref is employeeInfo[3]
			// defaultTimeFrame is employeeInfo[4]
			// custClass is employeeInfo[5]

			/*
			 * Creates ActivityID first before doing anything
			 */
			String activityId = "";
			try {
				activityId = createUniqueId(locId, slsId, dataPass, pProDatabasePath);
			} catch (SQLException err) {
				err.printStackTrace();
			}

			/*
			 * declares variables to put into SQL statement
			 */
			String schedDate = "";

			String schedTime = "";

			String activityType = "";

			String noteId = activityId + "_00001";

			String fupref = "";

			String phone = "";

			String location = "";

			String email = "";

			String docPath = "";

			String hide = "False";

			String notes = "";

			String eventId = "";

			String eventOwner = "";

			/*
			 * populates variables from the content in the request
			 */
			for (int positionInData = 4; positionInData < requestFromPhone.length; positionInData++) {
				String dataTag = requestFromPhone[positionInData].substring(0,
						requestFromPhone[positionInData].indexOf(':'));
				String content = requestFromPhone[positionInData]
						.substring(requestFromPhone[positionInData].indexOf(':') + 1);
				switch (dataTag) {
				case "schedDate":
					schedDate = content;
					break;
				case "schedTime":
					schedTime = content;
					break;
				case "activityType":
					activityType = content;
					break;
				case "fupref":
					fupref = content;
					break;
				case "phone":
					phone = content;
					break;
				case "location":
					location = content;
					break;
				case "EMail":
					email = content;
					break;
				case "templatePath":
					docPath = content;
					break;
				case "Notes":
					notes = content;
					break;
				case "calendarEventId":
					eventId = content;
					break;
				case "calendarEventOwner":
					eventOwner = content;
					break;
				}
			}

			/*
			 * adds entry into Notes table if there are notes to enter
			 */
			if (!notes.trim().isEmpty()) {
				try {
					DatabaseTools.updateNotesTable(pProDatabasePath, dataPass, noteId, "", notes);
				} catch (SQLException err) {
					err.printStackTrace();
				}
			}

			log("Creating new activity.");

			try {
				DatabaseTools.addNewActivity(pProDatabasePath, dataPass, activityId, idGivenFromPhone, slsId, schedDate,
						schedTime, activityType, noteId, fupref, phone.trim(), email.trim(), location.trim(),
						docPath.trim(), hide, eventId, eventOwner);
			} catch (SQLException err) {
				err.printStackTrace();
			}

			return activityId;
		}

		/**
		 * creates a new Activity or Customer ID by inputting information into
		 * the MasterID table in the database, getting the number that is
		 * generated, and deleting the new row in the MasterID table. The id is
		 * generated then in the format locationId-salesId-generatedNumber, and
		 * that Activity or CustomerId is returned
		 * 
		 * @return generated ActivityId or CustomerId
		 * @throws SQLException
		 */
		private String createUniqueId(String locId, String slsId, String dataPass, String pProDatabasePath)
				throws SQLException {
			/*
			 * creates a new autoNumber in the MasterID table, returns it, and
			 * deletes the entry that you created
			 */
			ResultSet autoNumberSet = DatabaseTools.getNewAutoNumber(pProDatabasePath, dataPass);
			String autoNumber;

			if (autoNumberSet.next()) {
				autoNumber = autoNumberSet.getString("AutoNumber");
				resultBuilder.append("AutoNumber for ActivityId retrieved. ");
			} else {
				autoNumber = "1";
				resultBuilder.append("Must retry creating AutoNumber. ");
			}

			// creates ActivityID
			String activityId = locId + "-" + slsId + "-" + autoNumber;

			// returns new ActivityID
			return activityId;
		}
	};

	/*
	 * 
	 * 
	 * 
	 * 
	 * ********************SYSTEM DEFAULT METHODS********************
	 * 
	 * 
	 * 
	 * 
	 * 
	 */

	/**
	 * prints out a message to the console with the date, time and ProspectorPro
	 * name appended to it
	 * 
	 * @param message
	 */
	private void log(String message) {
		stepNumber++;
		Write.writeLine(APP_NAME, message, counter, stepNumber);
	}

	/*
	 * 
	 * 
	 * 
	 * ************** Legacy code ******************
	 * 
	 * 
	 * 
	 */

	// /**
	// * Performs the quick search actions.
	// *
	// * @param id
	// * @param dbPass
	// * @param pProDbPath
	// * @param command
	// */
	// private void activityQs(String id, String dbPass, String pProDbPath,
	// String command) {
	// log("Quick searching activities for contact " + id + '.');
	//
	// try {
	// ResultSet activityList = DatabaseTools.searchActivities(dbPass,
	// pProDbPath, id);
	// serializeActivity(activityList, command, dbPass, pProDbPath);
	// } catch (SQLException err) {
	// err.printStackTrace();
	// }
	// }
	//
	// /**
	// * Begins the process to edit an activity in the database.
	// *
	// * @param dbPass
	// * @param pProDbPath
	// * @param id
	// * @param command
	// * @param thereIsContentInTheRequest
	// */
	// private void editActivity(String dbPass, String pProDbPath, String id,
	// String command,
	// boolean thereIsContentInTheRequest) {
	// log("Performing edit activity functions.");
	// if (thereIsContentInTheRequest) {
	// log("There is something to update");
	//
	// buildSQLQuery_editActivity(id, dbPass, pProDbPath);
	//
	// try {
	// ResultSet activityInformation = DatabaseTools.getActivity(pProDbPath,
	// dbPass, id);
	//
	// if (activityInformation.next()) {
	//
	// activityInformation.previous();
	// resultBuilder.append("Record updated. ");
	// serializeActivity(activityInformation, command, dbPass, pProDbPath);
	//
	// } else {
	// resultBuilder.append("Try recieving the edit activity information again.
	// ");
	// }
	// } catch (SQLException err) {
	// err.printStackTrace();
	// }
	// } else {
	// resultBuilder.append("Update was not requested. ");
	// }
	// }
	//
	// /**
	// * Performs a search for a activities based on date range, or a single
	// date.
	// *
	// * @param dbPass
	// * @param pProDbPath
	// * @param id
	// * @param command
	// */
	// private void dateRangeSearch(String dbPass, String pProDbPath, String id,
	// String command) {
	// log("Performing date and activities search functions.");
	//
	// try {
	// ResultSet search = null;
	//
	// if (command.equals("dateRangeSearch")) {
	// search = DatabaseTools.searchDateRange(dbPass, pProDbPath,
	// requestFromPhone[3], requestFromPhone[4]);
	// } else if (command.equals("singleDateSearch")) {
	// search = DatabaseTools.searchSingleDate(dbPass, pProDbPath,
	// requestFromPhone[3]);
	// }
	//
	// /*
	// * Takes the contents of the resultSet and converts it into a
	// * readable text string, which the phone can decipher when it
	// * receives it on the other side of the connection
	// */
	// serializeActivity(search, command, dbPass, pProDbPath);
	// } catch (SQLException err) {
	// err.printStackTrace();
	// }
	// }
	//
	// /**
	// * creates a SQL statement which edits an activity in the database
	// *
	// * @param idGivenFromPhone
	// * @return SQL statement which adds item to the database, or an empty
	// string
	// * if nothing is requested to be edited
	// */
	// private void buildSQLQuery_editActivity(String idGivenFromPhone, String
	// dataPass, String pProDatabasePath) {
	//
	// for (int index = 4; index < requestFromPhone.length; index++) {
	// // updates fields if something is entered in them
	// if (!requestFromPhone[index].trim().isEmpty()) {
	// // ensures correct syntax and that new notes are appended to
	// // old ones
	// String dataType = requestFromPhone[index].substring(0,
	// requestFromPhone[index].indexOf(':'));
	// String contentToUpdate =
	// requestFromPhone[index].substring(requestFromPhone[index].indexOf(':') +
	// 1);
	//
	// /*
	// * if there are notes to enter, retrieve the notes previously
	// * entered from the Notes table, copy them to the new note, and
	// * enter them into another SQL Statement and add them to the
	// * notes table
	// */
	//
	// try {
	// if (dataType.equals("Memo")) {
	// // content to update is the incremented note ID,
	// // getting
	// // it from this call
	// contentToUpdate = updateNotesTable(contentToUpdate, idGivenFromPhone,
	// dataPass,
	// pProDatabasePath);
	// }
	// DatabaseTools.updateSpecificActivityDetails(pProDatabasePath, dataPass,
	// dataType, contentToUpdate,
	// idGivenFromPhone);
	// } catch (SQLException err) {
	// err.printStackTrace();
	// }
	// }
	// }
	// }
	//
	// /**
	// * Creates or updates an existing note entry in the Prospector Pro
	// database
	// *
	// * @param partTwo
	// * @param idGivenFromPhone
	// * @return
	// * @throws SQLException
	// */
	// private String updateNotesTable(String partTwo, String idGivenFromPhone,
	// String dataPass, String pProDatabasePath)
	// throws SQLException {
	// log("Entering createOrUpdateNote method with memo " + partTwo + " and ID
	// " + idGivenFromPhone);
	//
	// ResultSet notesList = DatabaseTools.getNotesInformation(pProDatabasePath,
	// dataPass, idGivenFromPhone);
	//
	// /*
	// * noteIdIncrement is initialized to blank, so that we can check in the
	// * method above this one to see if any notes were added. If the
	// * noteIdIncrement field has content in it, then notes were entered into
	// * the database.
	// */
	// String noteIdIncrement;
	// String noteId;
	// String originalNoteId;
	// try {
	// notesList.next();
	// noteId = notesList.getString("NoteID");
	// originalNoteId = notesList.getString("OrigNoteID");
	//
	// // incrementing the NoteID
	// int noteIdPart = Integer.parseInt(noteId.substring(noteId.indexOf('_') +
	// 1));
	//
	// String noteIdInteger = "" + (noteIdPart + 1);
	// int lengthOfNumber = noteIdInteger.length();
	//
	// /*
	// * compares length of NoteID increment to its maximum possible
	// * length pads zeros to the beginning of it until it is the same
	// */
	// for (int amountOfZeroesToAdd = 5 - lengthOfNumber; amountOfZeroesToAdd >
	// 0; amountOfZeroesToAdd--) {
	// noteIdInteger = "0" + noteIdInteger;
	// }
	//
	// noteIdIncrement = idGivenFromPhone + "_" + noteIdInteger;
	// } catch (Exception err) {
	// log("Previous notes were not entered, creating for the first time");
	// noteIdIncrement = idGivenFromPhone + "_00001";
	// originalNoteId = noteIdIncrement;
	// }
	//
	// // puts new note into new Note row, with incremented
	// // NoteID
	// log("Creating new entry in the notes table, Note ID is " +
	// noteIdIncrement);
	//
	// DatabaseTools.updateNotesTable(pProDatabasePath, dataPass,
	// noteIdIncrement, originalNoteId, partTwo);
	// resultBuilder.append("Notes added to table. ");
	//
	// return noteIdIncrement;
	// }
}