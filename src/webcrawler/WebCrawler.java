package webcrawler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The WebCrawler class signifies a connection to the MariaDB app.
 */
public class WebCrawler {
	// Connection to the database
	private final Connection connection;
	// Reference to the WebCrawlerServer so that client
	// accesses to the database can be properly synchronized 
	private final WebCrawlerServer lock;

	// TODO: rep invariant, abstraction function

	/**
	 * Instantiate a WebCrawler object by creating a connection
	 * to the MariaDB app.
	 * 
	 * @param lock reference to the server that instantiated this object
	 * @throws SQLException unable to create a connetion
	 */
	public WebCrawler(WebCrawlerServer lock) throws SQLException {
		this.lock = lock;
		connection = DriverManager.getConnection("jdbc:mariadb://localhost:3306/?user=root");
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
		case "help":
			output = help();
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
	 * Closes the connection. This WebCrawler Object is no longer
	 * usable after calling this method.
	 */
	public void close() {
		try {
			connection.close();
		} catch (SQLException e) {
			// TODO: what happens if the connection isn't able to close successfully? does it matter?
			e.printStackTrace();
		}
	}

	/**
	 * @return nicely formatted text with information
	 * on all available commands
	 */
	private String help() {
		return "\n> help\n\tThis text.\n> use [db]\n\tSwitches to database db."
		        + "\n\tIf the database doesn't exist, a new one is created to switch to.\n";
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
		if (database == null || database.equals("")) return "ERROR: database not specified";
		try {
			Statement stmt = connection.createStatement();
			stmt.executeQuery("create database if not exists " + database);
			stmt.executeQuery("use " + database);
			stmt.close();
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
 *		- figure out what SQL commands I need to use to store the data I need for the webcrawler
 *		- read the JDBC basics tutorial to figure out how to properly use SQL commands in Java
 */

/*
 * have a table that has a bunch of seed websites and whether or not they've been accessed
 * create a command that initializes the table by deleting it if it already existed, then adding all the websites with "accessed" as false
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
