package webcrawler;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The WebCrawler class signifies a connection to the MariaDB app.
 */
public class WebCrawler {
	private static final String DEFAULT_DATABASE = "webcrawler";
	private String currentDatabase;
	// Connection to the database
	private final Connection connection;
	// Reference to the WebCrawlerServer so that client
	// accesses to the database can be properly synchronized 
	private final WebCrawlerServer databaseLock;
	// A map from URLs to threads that are currently running
	private Map<String, Thread> threads;

	// Rep invariant:
	//		databaseLock is the server that created this WebCrawler
	//		connection != null
	//		threads != null
	// Abstraction function:
	//		Represents a client that interacts with a MariaDB database.

	/**
	 * Instantiates a WebCrawler object from a given connection
	 * to the MariaDB app. Automatically starts by using the
	 * DEFAULT_DATABASE, and by initializing its tables.
	 * 
	 * @param lock reference to the server that instantiated this object
	 * @param connection an established connection to the MariaDB app
	 * @throws SQLException unable to create a connetion
	 */
	public WebCrawler(WebCrawlerServer databaseLock, Connection connection) throws SQLException {
		this.databaseLock = databaseLock;
		this.connection = connection;
		this.threads = new HashMap<String, Thread>();
		this.use(DEFAULT_DATABASE);
		this.init();
	}

	/**
	 * @return the current database this client is using
	 */
	public String getCurrentDatabase() {
		return currentDatabase;
	}

	/**
	 * Executes a command related to web crawling and the MariaDB app.
	 * Use the help command to see a description of all available commands.
	 * The effect of using this method through multiple instantiations of
	 * this object simultaneously is undefined. Synchronization must be
	 * used to ensure no conflict between executed commands.
	 * 
	 * @param input the client's input
	 * @return a message detailing the effect of the command
	 * @throws IllegalArgumentException input doesn't correspond to any command
	 */
	public String execute(String input) throws IllegalArgumentException {
		// Interpret the input string
		String[] splitStr = input.split(" +", 2);
		String command = splitStr[0].toLowerCase();
		String arg = null;
		if (splitStr.length > 1) arg = splitStr[1];

		// Store the output of the command
		String output = null;

		// Decide which command to execute
		switch (command) {
		case "drop":
			output = drop(arg);
			break;
		case "help":
			output = help();
			break;
		case "init":
			output = init();
			break;
		case "pause":
			output = pause();
			break;
		case "resume":
			output = resume();
			break;
		case "sanitize":
			output = sanitize();
			break;
		case "start":
			output = start(arg);
			break;
		case "stop":
			output = stop();
			break;
		case "use":
			output = use(arg);
			break;
		default:
			throw new IllegalArgumentException();
		}

		return output;
	}

	/**
	 * This method executes the SQL "drop database" command.
	 * Defaults to dropping the DEFAULUT_DATABASE if none
	 * is specified. Only executes the command if no threads
	) * are running.
	 * 
	 * @param database the name of the database to drop
	 * @return a message detailing the effect of this method
	 */
	private String drop(String database) {
		// Make sure no threads are currently running on the specified database
		databaseLock.stopDatabase(database);

		if (database == null || database.equals("")) database = DEFAULT_DATABASE;

		try (Statement stmt = connection.createStatement()) {
			synchronized (databaseLock) {
				stmt.executeUpdate("drop database if exists " + database + ";");
			}
			return "dropped database " + database;
		} catch (SQLException e) {
			e.printStackTrace();
			return "ERROR: unable to drop database " + database;
		}
	}

	/**
	 * @return nicely formatted text with information
	 * on all available commands
	 */
	private String help() {
		// TODO: update this when methods are updated
		return "\n> drop [db]\n\tDrops the specified database.\n\tIf none is specified, drops the '" + DEFAULT_DATABASE
		        + "' database." + "\n> help\n\tThis text." + "\n> init\n\tInitializes the 'seed' table."
		        + "\n> pause\n\tSame as the stop command, but the state of the crawler is saved."
		        + "\n> resume\n\tResumes the state saved by the pause command."
		        + "\n> sanitize\n\tRefreshes the database by removing any job postings that no longer exist."
		        + "\n> start [threads]\n\tStarts the web crawler with the given number of threads."
		        + "\n\tIf no thread number is specified, the crawler is started with one thread."
		        + "\n> stop\n\tStops all threads started by this client."
		        + "\n> use [db]\n\tSwitches to database db.\n\tIf none is specified, uses the '" + DEFAULT_DATABASE
		        + "' database." + "\n\tIf the database doesn't exist, a new one is created to switch to.\n";
	}

	/**
	 * Creates or replaces the table 'seed'. Each row contains a
	 * site and a 'visited' bit. Populates the table with a
	 * set of seed sites.
	 * 
	 * @return a message detailing the effect of this method
	 */
	private String init() {
		// Make sure no threads are currently running on the current database
		databaseLock.stopDatabase(currentDatabase);

		try (Statement stmt = connection.createStatement()) {
			synchronized (databaseLock) {
				// Create or replace the table
				stmt.executeUpdate("create or replace table seed(site VARCHAR(255), visited bit default 0);");

				// Insert all seed sites into the table
				try (BufferedReader br = new BufferedReader(new FileReader("seedSites.txt"))) {
					String line;
					while ((line = br.readLine()) != null) {
						stmt.executeUpdate("insert into seed values('" + line + "', 0);");
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			// TODO: add two more tables for links seen and jobs?
			// TODO: add a fourth table to store the current thread state?
			return "initialized a new seed table";
		} catch (SQLException e) {
			e.printStackTrace();
			return "ERROR: unable to initialize new seed table";
		}
	}

	/**
	 * Stops all of the threads that are currently running.
	 * Saves the state of all of these threads so that they
	 * can be resumed later on.
	 * 
	 * @return a message detailing the effect of this method
	 */
	private String pause() {
		// TODO: store all currently running threads (URLs) in a special table in the database
		// if no threads were running, say ERROR no threads running
		// call stop()
		return "all previously running threads are now paused";
	}

	/**
	 * Restarts all threads in the current database that were
	 * previously paused. If there are no such threads, this
	 * method does nothing.
	 * 
	 * @return a message detailing the effect of this method
	 */
	private String resume() {
		// TODO: collect all URLs that exist in the pause table of the database into a list
		// if the list is empty, say ERROR no threads were started
		// call spawnThreads(theListThatWasJustMade)
		return "all previously paused threads are now running";
	}

	/**
	 * Refreshes the database so that all job postings that no
	 * longer exist online are removed.
	 * 
	 * @return a message detailing the effect of this method
	 */
	private String sanitize() {
		// TODO: go through each job posting's url and check if there is still a job posting
		return "database has been sanitized";
	}

	/**
	 * Starts the web crawling from a given number of seed pages.
	 * 
	 * @param num number of threads to start the web crawling with
	 * @return a message detailing the effect of this method
	 */
	private String start(String num) {
		int threadCount;
		if (num == null) {
			threadCount = 1;
		} else {
			try {
				threadCount = Integer.valueOf(num);
			} catch (NumberFormatException e) {
				return "ERROR: please input a valid number of threads";
			}
		}

		List<String> seedPages = new ArrayList<String>();

		for (int i = 0; i < threadCount; i++) {
			// TODO: getRandomSeed();
			// String seed = getRandomSeed();
			// if (seed == null) break;
			// else seedPages.add(seed);
		}

		if (seedPages.size() == 0) {
			return "ERROR: no more seeds to start threads from";
		}

		spawnThreads(seedPages);

		return "started " + Integer.toString(seedPages.size()) + " threads";
	}

	/**
	 * Stops all of the threads that are currently running.
	 * Only the threads that have been started by this client
	 * are stopped.
	 * 
	 * @return a message detailing the effect of this method
	 */
	public String stop() {
		synchronized (threads) {
			while (threads.size() > 0) {
				Thread removedThread = threads.remove(0);
				removedThread.interrupt();
			}
		}

		return "stopped all threads";
	}

	/**
	 * This method executes the SQL "use" command. If the
	 * database specified doesn't exist, a new database is
	 * created and used with the specified name. 
	 * 
	 * @param database the name of the database to use
	 * @return a message detailing the effect of this method
	 */
	private String use(String database) {
		// Make sure all threads that this client was running are stored
		pause();

		if (database == null || database.equals("")) database = DEFAULT_DATABASE;
		currentDatabase = database;

		try (Statement stmt = connection.createStatement()) {
			synchronized (databaseLock) {
				stmt.executeUpdate("create database if not exists " + database);
			}
			stmt.executeUpdate("use " + database);
			return "using database " + database;
		} catch (SQLException e) {
			e.printStackTrace();
			return "ERROR: unable to use database " + database;
		}
	}

	/**
	 * TODO
	 * @param sites
	 */
	private void spawnThreads(List<String> sites) {
		for (String site : sites) {
			// if database contains this site already, "continue;" to next site
			// add site to the database

			Thread tempThread = new Thread(new Runnable() {
				public void run() {
					// get HTML text from site
					// if it contains a job posting, store it in the database
					// List<String> newSites = find all urls
					// spawnThreads(newSites);

					// This thread has now completed
					synchronized (threads) {
						threads.remove(site);
						if (threads.size() == 0) {
							// TODO: print "Dead end reached..." ?
						}
					}
				}
			});

			synchronized (threads) {
				threads.put(site, tempThread);
			}

			tempThread.start();
		}
	}
}

// TODO: create a list of like 100s of seed pages
// TODO: figure out a way to sanitize the database on a set interval

// TODO commands:
// view - prints all stored job postings
//		- should be options to print a subset of jobs, or to print thread websites...

