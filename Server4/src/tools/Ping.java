package tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Ping {

	private static final String CLASS_SIG = "Ping";
	private static long nullRequests = 0;
//	private static HashMap<InetAddress, Long> requests = new HashMap<>();

	/**
	 * Sends a specified message back to the client. Called from the public
	 * methods in this class.
	 * 
	 * @param welcomeSocket,
	 *            the Server socket we use to create the new socket with.
	 * @param command,
	 *            the string to send to the client.
	 */
	private static void sendMessageToClient(ServerSocket welcomeSocket, String command) {
		try {
			Socket connectionSocket = welcomeSocket.accept();

			BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));

			PrintWriter outToClient = new PrintWriter(connectionSocket.getOutputStream());

			/*
			 * get string from CLIENT for server command and information to use
			 */
			String message = inFromClient.readLine();
			log("recieved request " + message + " from IP " + connectionSocket.getInetAddress()
					+ " while data is initializing, sending wait command back.");
			outToClient.println(command);
			outToClient.flush();

			outToClient.flush();
			outToClient.close();

			connectionSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void logRequest(String values, Socket socket) {
//		InetAddress address = socket.getInetAddress();
//		Long count = requests.get(address);

//		if (count != null) {
//			log("Getting a request from an IP we have seen before");
//			count++;
//			requests.replace(address, count);
//		} else {
//			log("Getting a request from a new IP address");
//			count = new Long(1);
//			requests.put(address, count);
//		}

		if (values != null) {
			log("recieved command " + values + " from IP " + socket.getInetAddress());
		} else {
			nullRequests++;
			log("recieved null request from IP " + socket.getInetAddress() + " total null requests so far: "
					+ nullRequests);
		}
	}

	/**
	 * Sends a message to the client machine telling them to wait while data
	 * initializes.
	 * 
	 * @param welcomeSocket,
	 *            the ServerSocket we use to connect the new socket with.
	 */
	public static void sendWaitMessage(ServerSocket welcomeSocket) {
		sendMessageToClient(welcomeSocket, "Please wait, initializing data.");
	}

	/**
	 * Called automatically from the authentication server.
	 * 
	 * @param welcomeSocket,
	 *            the Server socket to connect the new socket with.
	 */
	public static void sendPing(ServerSocket welcomeSocket) {
		sendMessageToClient(welcomeSocket, "Server is active.");
	}

	/**
	 * Sends an error message to the client saying that databases have to be
	 * initialized.
	 * 
	 * @param welcomeSocket,
	 *            the Server socket to connect the new socket with.
	 */
	public static void sendErrorMessage(ServerSocket welcomeSocket) {
		sendMessageToClient(welcomeSocket, "Error in initializing databases.");
	}

	private static void log(String message) {
		Write.writeLine(CLASS_SIG, message);
	}

}
