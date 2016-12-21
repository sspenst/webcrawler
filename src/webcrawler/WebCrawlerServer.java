package webcrawler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a server that is connected to the MariaDB app.
 * Multiple clients can connect to the server simultaneously and are able
 * to send requests in order to retrieve relevant information from the database.
 */
public class WebCrawlerServer {
	// List of all clients currently connected to the database
	private List<WebCrawler> clients;

	// Default port number where the server listens for connections.
	private static final int PORT = 4949;
	private ServerSocket serverSocket;

	// Rep invariant:
	//		clients != null
	//		serverSocket != null
	// Abstraction function:
	//		Represents a server that interacts with a local MariaDB database.
	//		A client can connect to the server to interact with the database.
	// Thread safety argument:
	//		The database is the main structure that is shared between threads.
	//		All client accesses are synchronized to this, therefore the
	//		accesses to the database from this class do not cause problems,
	//		which makes WebCrawlerServer threadsafe.

	/**
	 * Make a RestaurantDBServer that listens for connections on port.
	 * Initialize the restaurant database.
	 * 
	 * @param port
	 *            port number, requires 0 <= port <= 65535
	 */
	public WebCrawlerServer() throws IOException {
		serverSocket = new ServerSocket(PORT);
		clients = new ArrayList<WebCrawler>();
	}

	/**
	 * Run the server, listening for connections and handling them.
	 * 
	 * @throws IOException
	 *             if the main server socket is broken
	 */
	public void serve() throws IOException {
		WebCrawlerServer databaseLock = this;
		while (true) {
			// block until a client connects
			final Socket socket = serverSocket.accept();
			// create a new thread to handle that client
			Thread handler = new Thread(new Runnable() {
				public void run() {
					try {
						WebCrawler webCrawler = null;
						try (Connection connection = DriverManager
						        .getConnection("jdbc:mariadb://localhost:3306/?user=root")) {
							webCrawler = new WebCrawler(databaseLock, connection);
							synchronized (clients) {
								clients.add(webCrawler);
							}
							handle(socket, webCrawler);
						} catch (SQLException e) {
							e.printStackTrace();
						} finally {
							socket.close();
							synchronized (clients) {
								clients.remove(webCrawler);
							}
						}
					} catch (IOException ioe) {
						// this exception wouldn't terminate serve(),
						// since we're now on a different thread, but
						// we still need to handle it
						ioe.printStackTrace();
					}
				}
			});
			// start the thread
			handler.start();
		}
	}

	/**
	 * Handle one client connection. Returns when client disconnects.
	 * 
	 * @param socket
	 *            socket where client is connected
	 * @throws IOException
	 *             if connection encounters an error
	 */
	private void handle(Socket socket, WebCrawler webCrawler) throws IOException {
		System.err.println("client connected");

		// get the socket's input stream, and wrap converters around it
		// that convert it from a byte stream to a character stream,
		// and that buffer it so that we can read a line at a time
		BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

		// similarly, wrap character=>bytestream converter around the
		// socket output stream, and wrap a PrintWriter around that so
		// that we have more convenient ways to write Java primitive
		// types to it.
		PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

		try {
			// each request is a single line containing a number
			for (String line = in.readLine(); line != null; line = in.readLine()) {
				System.err.println("request: " + line);
				try {
					String output = webCrawler.execute(line);

					// Output to server and client
					System.err.println("reply: " + output);
					out.println(output);
				} catch (IllegalArgumentException e) {
					// complain about ill-formatted request
					String response = "ERROR: unsupported command";
					System.err.println("reply: " + response);
					out.println(response);
				}
				// important! our PrintWriter is auto-flushing, but if it were not:
				// out.flush();
			}
		} finally {
			System.err.println("client disconnected");
			out.close();
			in.close();
		}
	}

	/**
	 * Stop all threads that are running on a given database.
	 * 
	 * @param database the database to halt
	 */
	public void stopDatabase(String database) {
		synchronized (clients) {
			for (WebCrawler client : clients) {
				if (client.getCurrentDatabase().equals(database)) {
					client.stop();
				}
			}
		}
	}

	/**
	 * Start a server running on a specified port. If no port is
	 * specified, port 4949 will be used.
	 * 
	 * @param args the port that the server will run on
	 */
	public static void main(String[] args) {
		try {
			WebCrawlerServer server = new WebCrawlerServer();
			server.serve();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
