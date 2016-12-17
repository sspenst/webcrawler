package webcrawler;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The WebCrawler class signifies a connection to the MariaDB app.
 */
public class WebCrawler {
	private static final String DEFAULT_DATABASE = "webcrawler";
	// Connection to the database
	private final Connection connection;
	// Reference to the WebCrawlerServer so that client
	// accesses to the database can be properly synchronized 
	private final WebCrawlerServer lock;

	// TODO: create a database variable for the class, if necessary
	// TODO: threads variables to store active threads (maybe a thread class?)

	// TODO: rep invariant, abstraction function

	/**
	 * Instantiates a WebCrawler object from a given connection
	 * to the MariaDB app. Automatically starts by using the
	 * database 'webcrawler', and by initializing its tables.
	 * 
	 * @param lock reference to the server that instantiated this object
	 * @param connection an established connection to the MariaDB app
	 * @throws SQLException unable to create a connetion
	 */
	public WebCrawler(WebCrawlerServer lock, Connection connection) throws SQLException {
		this.lock = lock;
		this.connection = connection;
		this.use(DEFAULT_DATABASE);
		this.init();
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
	 * Defaults to dropping the 'webcrawler' database if none
	 * is specified. Only executes the command if no threads
	 * are running.
	 * 
	 * @param database the name of the database to drop
	 * @return a message detailing the effect of this method
	 */
	private String drop(String database) {
		if (database == null || database.equals("")) database = DEFAULT_DATABASE;

		try (Statement stmt = connection.createStatement()) {
			// TODO: make sure no active threads are running before dropping
			synchronized (lock) {
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
		return "\n> drop [db]\n\tDrops the specified database." + "\n> help\n\tThis text."
		        + "\n> init\n\tInitializes the 'seed' table." + "\n> use [db]\n\tSwitches to database db."
		        + "\n\tIf the database doesn't exist, a new one is created to switch to.\n";
	}

	/**
	 * Creates or replaces the table 'seed'. Each row contains a
	 * site and a 'visited' bit. Populates the table with a
	 * set of seed sites.
	 * 
	 * @return a message detailing the effect of this method
	 */
	private String init() {
		try (Statement stmt = connection.createStatement()) {
			synchronized (lock) {
				// TODO: make sure no active threads are running before initializing
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
			return "initialized a new seed table";
		} catch (SQLException e) {
			e.printStackTrace();
			return "ERROR: unable to initialize new seed table";
		}
	}

	/**
	 * TODO: this
	 * 
	 * @param arg
	 * @return
	 */
	private String start(String arg) {
		// TODO: check if you are using a database
		// TODO: check if table of sites exists

		int threads;
		if (arg == null) {
			threads = 1;
		} else {
			try {
				threads = Integer.valueOf(arg);
			} catch (NumberFormatException e) {
				return "ERROR: please input a valid number of threads";
			}
		}

		// TODO:
		// look through the table to get a list of unvisited links
		// pick threads links from the list (if there aren't enough links, don't start the crawler)
		// start the threads

		return arg;
	}

	/**
	 * TODO
	 * @return
	 */
	private String stop() {
		// TODO: safely stop all threads
		return null;
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
		if (database == null || database.equals("")) database = DEFAULT_DATABASE;

		try (Statement stmt = connection.createStatement()) {
			// TODO: make sure no active threads are running
			synchronized (lock) {
				stmt.executeQuery("create database if not exists " + database);
			}
			stmt.executeQuery("use " + database);
			return "using database " + database;
		} catch (SQLException e) {
			e.printStackTrace();
			return "ERROR: unable to use database " + database;
		}
	}
}

/*
 * TODO:
 *	- add start command
 *		- create a list of like 100s of seed pages
 *		- pick some pages and start the recursive web search thing
 *		- pages must be stored in the database
 *		- create a method to check if threads are running (in WebCrawlerServer)
 */

/*
 * when you start the crawler with a specified number of seed pages, it will pick random pages to start with, then will start the threads
 * ideally you should be able to execute other commands while the threads are going
 * if another client starts the crawler while a crawler has already been started, that crawler will check to see if there are enough
 * "not accessed" seed pages to start crawling from. if there are, then it starts threads also. each time any thread from any client needs
 * to access the database, the access has to be synchronized. so they will add all accessed pages to the database, and all jobs. 
 */

/*
TODO: what happens if all of the threads running can't find any more links?
i.e. all of the links on the current pages have already been visited, so there is nowhere to go
just print nothing else can be found I guess "Dead end reached..."

commands:
start [threads] - this command will do nothing if open has not been called
	- starts the webcrawler with the specified number of threads (default is 1)
	- should be an option for how long it searches the web
	- automatically calls sanitize every once and a while...
stop - stops the webcrawling
sanitize - manually refresh the open database
clear - get rid of all information in the database
view - prints all stored job postings
	- should be options to print a subset of jobs, or to print thread websites...

webcrawler:
List<String> allPages
List<String> currentPages
- add every page in currentPages to allPages
- make a thread for each page in currentPages
- clear currentPages before any thread does anything
- each thread finds all the links on its page and adds them to currentPages
- restart

this is a BFS
*/
