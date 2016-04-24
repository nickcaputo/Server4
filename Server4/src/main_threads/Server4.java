package main_threads;

/**
 *	@author Nicholas Caputo, (847) 630 7370
 *  
 *  This software was created by Nicholas Caputo for Rainmaker Software
 *  It is for deployment to clients to enable them to access their data from
 *  Rainmaker Software applications over the phone, it is coupled with
 *  an authentication server application running at
 *  
 *  @address 4215 E 60th Street, Davenport, IA 52807
 *  
 *  and it serves data to a suite of mobile apps written for Rainmaker Software.
 *
 *  Rainmaker Software, Inc.
 *  Date of this release:  3/4/2016 17:15
 *  Date of original (version 1.0) creation: 6/9/2015, 15:36
 */

import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.InputStreamReader;

import javax.swing.*;

import app_threads.ProspectorPro;
import app_threads.Inventory;
import app_threads.Timecard;
import tools.SavedValues;
import tools.DatabaseTools;
import tools.Ping;
import tools.Write;

public class Server4 extends Thread {

	private static final String APP_NAME = "Main Server";

	private static ServerSocket welcomeSocket;
	private static Map<Long, ProspectorPro> pProThreads;
	private static Map<Long, Inventory> inventoryThreads;
	private static Map<Long, Timecard> timecardThreads;
	private static Map<Long, Socket> sockets;
	private static String databasePass;
	private static String rmAppServerPath;
	private static URL logoPath;
	private static Image logo;
	private static boolean logoIsPresent;
	/*
	 * the addUserPathname variable is only used when adding a user from the
	 * listener UI, so we can still use the pathname field either way
	 */
	private static String dataPass;
	private static String values;
	private static JFrame frame;
	private static JFrame addUserFrame;
	private static JFrame promptForSalesId;
	private static JFrame selectPathFrame;
	private static String nameOfSalespersonToAdd;
	private static String companyOfSalespersonToAdd;
	private static boolean isASalesManager;
	private static boolean shouldShowLocation;
	private static long counter;
	private static boolean initializing;

	/**
	 * starts the GUI, declares the driver for the database access creates the
	 * server socket for incoming connections, begins initializing all
	 * databases, and starts the main worker thread
	 */
	public static void main(String arguments[]) {
		setUpGlobals();

		setUpWelcomeSocket(SavedValues.getPort());

		checkDatabaseConnectivity();

		checkRMAPPServerDbExists();

		initializeGUI();

		recieveRequests.start();
		prompt.start();
		sendMessageBack.start();
	}

	/**
	 * Assigns initial state to non-final global variables
	 */
	private static void setUpGlobals() {
		pProThreads = new HashMap<>();
		inventoryThreads = new HashMap<>();
		timecardThreads = new HashMap<>();
		sockets = new HashMap<>();
		databasePass = "MiguelAngel";
		rmAppServerPath = "";
		logoIsPresent = false;

		dataPass = "MiguelAngel"; // default pass
		values = "";
		initializing = false;

		counter = 0;
	}

	private static void setUpWelcomeSocket(int port) {
		try {
			welcomeSocket = new ServerSocket(port);
		} catch (IOException err) {
			err.printStackTrace();
			log("Exiting application with error code 1");
			System.exit(1);
		}
	}

	private static void checkDatabaseConnectivity() {
		try {
			Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
		} catch (ClassNotFoundException err) {
			err.printStackTrace();
			log("Exiting applcation with error code 2");
			System.exit(2);
		}
	}

	private static void checkRMAPPServerDbExists() {
		File rmAppServerDb = new File("RMAPPServer.mdb");
		rmAppServerPath = rmAppServerDb.getAbsolutePath();
	}

	private static void initializeGUI() {
		frame = new JFrame("Rainmaker Mobile App Server");
		File pProLogo = new File("PProLogo.png");
		log("RMAPPServer database is in " + rmAppServerPath);

		try {
			logoPath = pProLogo.toURI().toURL();
		} catch (MalformedURLException err) {
			err.printStackTrace();
		}

		logo = Toolkit.getDefaultToolkit().getImage(logoPath);
		logoIsPresent = logo != null;

		if (logoIsPresent) {
			frame.setIconImage(logo);
		}
	}

	/**
	 * thread that handles incoming requests from mobile phones
	 */
	private static Thread recieveRequests = new Thread() {
		@Override
		public void run() {
			try {
				initialConnection();
			} catch (IOException | InterruptedException err) {
				err.printStackTrace();
			}
		}

		/**
		 * for every connection coming through the welcome socket, gives it its
		 * own client socket and sends all data through that. Determines which
		 * service is requesting the information and then creates a new thread
		 * of that type to complete that task
		 */
		private void initialConnection() throws IOException, InterruptedException {
			try {
				while (true) {
					if (counter > 0) {
						acceptIncomingConnections();
					} else {
						doFirstInitialization();
					}
				}
			} catch (IOException | NullPointerException err) {
				welcomeSocket.close();
				err.printStackTrace();
				counter++;
			}
		}

		/**
		 * This method contains the code which accepts an incoming connection,
		 * retrieves the command from it and starts the proper thread to handle
		 * it.
		 * 
		 * @throws IOException,
		 *             in case the connection is reset
		 */
		private void acceptIncomingConnections() throws IOException {
			/*
			 * creates individual socket for incoming connection
			 */
			Socket connectionSocket = welcomeSocket.accept();

			log("Number of requests so far: " + counter + ". Starting new process.");
			sockets.put(counter, connectionSocket);

			/*
			 * handle requests normally if the database is initialized already
			 */
			BufferedReader inFromClient = new BufferedReader(
					new InputStreamReader(sockets.get(counter).getInputStream()));

			values = inFromClient.readLine();
			Ping.logRequest(values, sockets.get(counter));

			String command = "no command";

			if (values != null) {
				startProperAppThread(command);
			}

			counter++;
		}

		private void startProperAppThread(String command) {
			log("startProperAppThread method starting");

			String[] connectionData = values.split(SavedValues.getItemDelimiter());
			if (connectionData.length >= 2) {
				command = connectionData[1];
			}

			// test to see what program to create a thread
			// for
			switch (connectionData[0]) {
			case "ProspectorPro":
				pProThreads.put(counter, new ProspectorPro(command, counter));
				break;
			case "Inventory":
				inventoryThreads.put(counter, new Inventory(command, counter));
				break;
			case "Timecard":
				timecardThreads.put(counter, new Timecard(command, counter));
				break;
			}
		}

		/**
		 * Sends wait command back to the phone while the database initializes
		 * for the first time.
		 * 
		 * @throws InterruptedException
		 *             if the process of waiting is interrupted by a call to
		 *             notify()
		 */
		private void doFirstInitialization() throws InterruptedException {
			/*
			 * on first startup, load up the databases twice
			 */
			DatabaseTools.initializeAllDatabases();

			/*
			 * waits for sendMessageBack to complete, which happens once all of
			 * the databases are initialized
			 */
			sendMessageBack.join();

			/*
			 * now the initialConnection() method will complete, the code will
			 * return to the main run() method in the thread, and the while loop
			 * in that will restart the method
			 */
		}
	};

	/**
	 * handles the GUI, and sends requests for new users of the app to the
	 * Authentication server.
	 */
	private static Thread prompt = new Thread() {
		@Override
		public void run() {
			synchronized (this) {
				try {
					startConnection();
				} catch (InterruptedException err) {
					err.printStackTrace();
				}
			}
		}

		/**
		 * the UI that comes up when the program is started
		 * 
		 * @throws InterruptedException
		 */
		public void startConnection() throws InterruptedException {

			JLabel label = new JLabel(SavedValues.getGUINotice(), SwingConstants.CENTER);
			JButton askToAddUser = new JButton("Request to add a user");
			Dimension standard = new Dimension(425, 150);

			frame.getContentPane().add(label, BorderLayout.CENTER);
			frame.getContentPane().add(askToAddUser, BorderLayout.SOUTH);
			frame.setSize(standard);
			frame.setResizable(false);
			frame.setVisible(true);
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

			Set<String> filePaths = new LinkedHashSet<>();
			Set<String> salesIds = new LinkedHashSet<>();
			Set<String> locIds = new LinkedHashSet<>();

			// methods performed when button is pressed, there is a lot here
			askToAddUser.addActionListener(event -> {
				if (promptForSalesId != null) {
					promptForSalesId.dispose();
				}

				if (addUserFrame != null) {
					addUserFrame.dispose();
				}

				if (selectPathFrame != null) {
					selectPathFrame.dispose();
				}

				try {
					ResultSet set = DatabaseTools.getTopDatabases(rmAppServerPath, dataPass);

					int numberFilepaths = 0;
					String addUserPath = "";

					while (set.next()) {
						addUserPath = set.getString("FilePath");
						filePaths.add(addUserPath);
						numberFilepaths++;
					}

					/*
					 * if there is only one Prospector Pro installation
					 */
					if (numberFilepaths == 1) {
						log("That was the only filepath in the database");
						getUserInformation(salesIds, locIds, databasePass, addUserPath);
					} else {
						/*
						 * if there is more than one Prospector Pro installation
						 */
						selectPathFrame = new JFrame("Select installation");
						if (logoIsPresent) {
							selectPathFrame.setIconImage(logo);
						}

						JPanel selectFilePath = new JPanel(new FlowLayout());
						JLabel selectLabel = new JLabel("Which file path should we use");
						JComboBox<String> selectPath = new JComboBox<String>(
								filePaths.toArray(new String[filePaths.size()]));
						JButton chooseFilePath = new JButton("Choose file path");
						selectFilePath.add(selectLabel);
						selectFilePath.add(selectPath);

						selectPathFrame.add(selectFilePath, BorderLayout.PAGE_START);
						selectPathFrame.add(chooseFilePath, BorderLayout.PAGE_END);
						selectPathFrame.pack();
						selectPathFrame.setResizable(false);
						selectPathFrame.setLocationRelativeTo(frame);
						selectPathFrame.setVisible(true);

						chooseFilePath.addActionListener(argument -> {
							try {
								String selectedPath = selectPath.getSelectedItem().toString();
								selectPathFrame.dispose();
								getUserInformation(salesIds, locIds, databasePass, selectedPath);
							} catch (SQLException err) {
								err.printStackTrace();
							}
						});
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
			});
		}

		/**
		 * populates a list of all people who are registered users on Prospector
		 * Pro, and allows you to select from them who to ask to add to the
		 * application
		 * 
		 * @param salesIds
		 * @param locIds
		 * @param databasePass
		 * @throws SQLException
		 */
		private void getUserInformation(Set<String> salesIds, Set<String> locIds, String databasePass, String path)
				throws SQLException {

			log("getting possible user information");

			ResultSet salesInformation = DatabaseTools.getSalesAndLocationIds(path, databasePass);

			while (salesInformation.next()) {
				String salesIdInList = salesInformation.getString("SlsId");
				String locIdInList = salesInformation.getString("LocId");

				if (salesIdInList != null) {
					salesIds.add(salesIdInList);
				}

				if (locIdInList != null) {
					locIds.add(locIdInList);
				}
			}

			promptForSalesId = new JFrame("Finding You");
			if (logoIsPresent) {
				promptForSalesId.setIconImage(logo);
			}
			JButton getSalesInformation = new JButton("Get information");

			JPanel salesIdLayout = new JPanel(new FlowLayout());
			JLabel salesIdLabel = new JLabel("Your Sales ID: ");
			JComboBox<String> salesIdEnter = new JComboBox<>(salesIds.toArray(new String[salesIds.size()]));
			salesIdLayout.add(salesIdLabel);
			salesIdLayout.add(salesIdEnter);

			JPanel locIdLayout = new JPanel(new FlowLayout());
			JLabel locIdLabel = new JLabel("Your Location ID: ");
			JComboBox<String> locIdEnter = new JComboBox<>(locIds.toArray(new String[locIds.size()]));
			locIdLayout.add(locIdLabel);
			locIdLayout.add(locIdEnter);

			promptForSalesId.add(salesIdLayout, BorderLayout.PAGE_START);
			promptForSalesId.add(locIdLayout, BorderLayout.CENTER);
			promptForSalesId.add(getSalesInformation, BorderLayout.PAGE_END);

			getSalesInformation.addActionListener(event -> {
				promptForSalesId.dispose();
				getSalespersonName(salesIdEnter.getSelectedItem().toString(), locIdEnter.getSelectedItem().toString(),
						path);
			});

			promptForSalesId.setVisible(true);
			promptForSalesId.setLocationRelativeTo(frame);
			promptForSalesId.setResizable(false);
			promptForSalesId.pack();
		}

		/**
		 * finds sales information regarding the new user of the app
		 * 
		 * @param salesId
		 * @param locationId
		 */
		private void getSalespersonName(String salesId, String locationId, String path) {
			log("getting detailed information about selected user at " + path);
			if (!(salesId.isEmpty() || locationId.isEmpty())) {
				try {
					ResultSet salesInformation = DatabaseTools.getSalespersonName(path, dataPass, salesId, locationId);

					while (salesInformation.next()) {
						nameOfSalespersonToAdd = salesInformation.getString("Slsname");
						companyOfSalespersonToAdd = salesInformation.getString("LocName");
						isASalesManager = salesInformation.getBoolean("SalesManager");
						shouldShowLocation = salesInformation.getBoolean("ShowLocation");
						addUserToDatabase();
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}

		/**
		 * allows you to review and send the information to add the user
		 */
		private void addUserToDatabase() {
			if (addUserFrame != null) {
				addUserFrame.dispose();
			}
			addUserFrame = new JFrame("Send request to add a user.");
			if (logoIsPresent) {
				addUserFrame.setIconImage(logo);
			}

			JPanel header = new JPanel(new GridLayout(4, 0));
			header.add(new JLabel(nameOfSalespersonToAdd, SwingConstants.CENTER));
			header.add(new JLabel(companyOfSalespersonToAdd, SwingConstants.CENTER));
			header.add(new JLabel("Is a Sales Manager: " + isASalesManager, SwingConstants.CENTER));
			header.add(new JLabel("Should show location: " + shouldShowLocation, SwingConstants.CENTER));

			JPanel userAndPassEntry = new JPanel(new GridLayout(4, 0));

			JPanel eMailLayout = new JPanel(new FlowLayout());
			JLabel eMailLabel = new JLabel("Business EMail: ");
			JTextField eMailEnter = new JTextField(15);
			eMailLayout.add(eMailLabel);
			eMailLayout.add(eMailEnter);

			JPanel eMailConfirmLayout = new JPanel(new FlowLayout());
			JLabel confirmEMailLabel = new JLabel("Confirm EMail: ");
			JTextField confirmEMailEnter = new JTextField(15);
			eMailConfirmLayout.add(confirmEMailLabel);
			eMailConfirmLayout.add(confirmEMailEnter);

			JPanel passwordLayout = new JPanel(new FlowLayout());
			JLabel passLabel = new JLabel("Password: ");
			JPasswordField passEnter = new JPasswordField(15);
			passwordLayout.add(passLabel);
			passwordLayout.add(passEnter);

			JPanel passwordConfirmLayout = new JPanel(new FlowLayout());
			JLabel confirmPassLabel = new JLabel("Confirm Password: ");
			JPasswordField confirmPassEnter = new JPasswordField(15);
			passwordConfirmLayout.add(confirmPassLabel);
			passwordConfirmLayout.add(confirmPassEnter);

			userAndPassEntry.add(eMailLayout);
			userAndPassEntry.add(eMailConfirmLayout);
			userAndPassEntry.add(passwordLayout);
			userAndPassEntry.add(passwordConfirmLayout);

			JButton send = new JButton("Send");

			addUserFrame.add(header, BorderLayout.PAGE_START);
			addUserFrame.add(userAndPassEntry, BorderLayout.CENTER);
			addUserFrame.add(send, BorderLayout.SOUTH);
			addUserFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			addUserFrame.setVisible(true);
			addUserFrame.setResizable(false);
			addUserFrame.setLocationRelativeTo(frame);
			addUserFrame.pack();

			send.addActionListener(event -> {
				char[] password = passEnter.getPassword();
				char[] confirmation = confirmPassEnter.getPassword();
				String pass = "";
				String confirm = "";
				String eMailEntered = eMailEnter.getText();
				String confirmEmailEntered = confirmEMailEnter.getText();
				JFrame messageFrame = new JFrame();

				for (int index = 0; index < password.length; index++) {
					pass = pass + password[index];
				}

				for (int index = 0; index < confirmation.length; index++) {
					confirm = confirm + confirmation[index];
				}

				/*
				 * Start the checks to make sure that the content entered is
				 * valid to send to the server
				 */
				if ((pass.length() < 5 || eMailEntered.length() < 5) && !eMailEntered.contains("@")) {
					/*
					 * first check, that password is long enough and email is in
					 * the correct format
					 */
					JOptionPane.showMessageDialog(messageFrame,
							"The password you enter has to be at least 5 characters long, and the eMail should be in the proper format.  Please correct them before proceeding.",
							"Password and email format rules", JOptionPane.INFORMATION_MESSAGE);
				} else if (eMailEntered.equals(confirmEmailEntered) && pass.equals(confirm)) {
					/*
					 * if email matches confirm, and password matches confirm
					 */
					try {
						Socket clientSocket = new Socket(SavedValues.getAuthIp(), SavedValues.getAuthPort());

						PrintWriter outToAuth = new PrintWriter(clientSocket.getOutputStream());

						/*
						 * sending values to server
						 */
						try {
							String sep = SavedValues.getContentSeparator();
							outToAuth.println("newUser" + sep + "name:" + nameOfSalespersonToAdd + sep
									+ "requestedEMail:" + eMailEntered + sep + "requestedPass:" + pass + sep
									+ "isASalesManager:" + isASalesManager + sep + "shouldShowLocation:"
									+ shouldShowLocation + sep + "company:" + companyOfSalespersonToAdd);

							outToAuth.flush();
						} catch (Exception e) {
							e.printStackTrace();
						}

						JOptionPane.showMessageDialog(messageFrame, "Your request was sent successfully!",
								"Sent successfully", JOptionPane.INFORMATION_MESSAGE);
						clientSocket.close();
						addUserFrame.dispose();
					} catch (IOException err) {
						err.printStackTrace();
						JOptionPane.showMessageDialog(messageFrame,
								"<html><body style='width: 100px'>There was a problem connecting to the server, this is the exception thrown: "
										+ err.toString() + "</body></html>",
								"Server output problem.", JOptionPane.INFORMATION_MESSAGE);
					}
				} else {
					/*
					 * if the email and password do not match their respective
					 * confirm lines
					 */
					JOptionPane.showMessageDialog(messageFrame,
							"The email and password combination wasn\'t matching it looks like.  Try it again.",
							"EMail or password mismatch", JOptionPane.INFORMATION_MESSAGE);
				}
			});
		}
	};

	/**
	 * this thread re-initializes the databases once every 30 minutes
	 * automatically
	 */
	private static Thread initializingData = new Thread() {
		@Override
		public void run() {
			while (true) {
				try {
					Thread.sleep(SavedValues.getTimeToRefresh()); // wait for 30
																	// minutes
					DatabaseTools.initializeAllDatabases();
				} catch (InterruptedException err) {
					err.printStackTrace();
				}
			}
		}
	};

	/**
	 * while the databases are having their first initialization when starting
	 * up, this thread sends a message back to any app that is requesting data
	 * from them, telling it to wait a second.
	 */
	private static Thread sendMessageBack = new Thread() {
		@Override
		public void run() {

			while (counter == 0) {
				Ping.sendWaitMessage(welcomeSocket);
			}

			while (counter == -1) {
				Ping.sendErrorMessage(welcomeSocket);
			}

			/*
			 * ensures that the data sets are refreshed every 30 minutes
			 */
			initializingData.start();
		}
	};

	/**
	 * returns the socket assigned to this counter
	 * 
	 * @param counter
	 * @return
	 */
	public static Socket getSocket(long counter) {
		return sockets.get(counter);
	}

	/**
	 * returns the pathname to the main directory for files, the RMAPPServer
	 * database, etc.
	 * 
	 * @return
	 */
	public static String getRMAppServerPath() {
		return rmAppServerPath;
	}

	/**
	 * accepts a string declaring the type of thread to be removed, and removes
	 * that thread, allowing the object calling it to self-destruct
	 * 
	 * @param type
	 * @param counter
	 */
	public static void removeThread(String type, long counter) {
		log("removing thread " + type + " with counter " + counter);

		switch (type) {

		case "ProspectorPro":
			pProThreads.remove(counter);
			log("Prospector Pro thread removed successfully." + System.lineSeparator());
			break;

		case "Inventory":
			inventoryThreads.remove(counter);
			log("Inventory thread removed successfully." + System.lineSeparator());
			break;

		case "Timecard":
			timecardThreads.remove(counter);
			log("Timecard thread removed successfully." + System.lineSeparator());
			break;

		default:
			log("Remove thread method called, but the request was " + type + System.lineSeparator());
			break;

		}

		// Execute garbage collection to make sure we are being efficient with
		// space.
		System.gc();
	}

	/**
	 * increments the counter in the main server application
	 */
	public static void incrementCounter() {
		counter++;
	}

	public static void setCounterToMinus1() {
		counter = -1;
	}

	/**
	 * returns the password to the selected database for this company
	 * 
	 * @return
	 */
	public static String getDatabasePass() {
		return databasePass;
	}

	/**
	 * returns whether the server is initializing
	 * 
	 * @return boolean value if the server is Initializing
	 */
	public static boolean isInitializing() {
		return initializing;
	}

	/**
	 * sets the status of whether the Server is initializing
	 * 
	 * @param status
	 *            of whether we are initializing now
	 */
	public static void setInitializing(boolean status) {
		initializing = status;
	}

	/**
	 * prints a message to the console with the current date and time attached
	 * to it
	 * 
	 * @param message
	 */
	private static void log(String message) {
		Write.writeLine(APP_NAME, message);
	}
}