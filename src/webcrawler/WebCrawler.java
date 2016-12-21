package webcrawler;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * The WebCrawler class signifies a connection to the MariaDB app.
 */
public class WebCrawler {
	private static final int MAX_SITE_LENGTH = 1023;
	private static final String DEFAULT_DATABASE = "webcrawler";
	private String currentDatabase;
	// Connection to the database
	private final Connection connection;
	// Reference to the WebCrawlerServer so that client
	// accesses to the database can be properly synchronized 
	private final WebCrawlerServer databaseLock;
	// A map from URLs to threads that are currently running
	// TODO: limit the size of threads so that it doesn't take forever to pause/stop?
	private final Map<String, Thread> threads;
	// TODO: make this a lock?
	private boolean createThreads;

	// Rep invariant:
	//		currentDatabase != null
	//		connection != null
	//		databaseLock is the server that created this WebCrawler
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
	 * @throws SQLException unable to create a connection
	 */
	public WebCrawler(WebCrawlerServer databaseLock, Connection connection) throws SQLException {
		this.databaseLock = databaseLock;
		this.connection = connection;
		this.threads = new HashMap<String, Thread>();
		this.createThreads = true;
		this.use(DEFAULT_DATABASE);
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
		case "threads":
			output = threads();
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
	 * are running.
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
		return "\n> drop [db]\n\tDrops the specified database.\n\tIf none is specified, drops the '" + DEFAULT_DATABASE + "' database."
		        + "\n> help\n\tThis text." + "\n> init\n\tInitializes the 'seeds', 'sites', 'jobs', and 'state' tables."
		        + "\n> pause\n\tSame as the stop command, but the state of the crawler is saved."
		        + "\n> resume\n\tResumes the state saved by the pause command."
		        + "\n> sanitize\n\tRefreshes the database by removing any job postings that no longer exist."
		        + "\n> start [threads]\n\tStarts the web crawler with the given number of threads."
		        + "\n\tIf no thread number is specified, the crawler is started with one thread."
		        + "\n> stop\n\tStops all threads started by this client." + "\n> threads\n\tPrints the number of threads currently running."
		        + "\n> use [db]\n\tSwitches to database db.\n\tIf none is specified, uses the '" + DEFAULT_DATABASE
		        + "' database.\n\tIf the database doesn't exist, a new one is created to switch to.\n";
	}

	/**
	 * Creates or replaces the 'seeds', 'sites', 'jobs',
	 * and 'state' tables. Populates the 'seeds' table with a
	 * set of seed sites, the rest are empty.
	 * 
	 * @return a message detailing the effect of this method
	 */
	private String init() {
		// Make sure no threads are currently running on the current database
		databaseLock.stopDatabase(currentDatabase);

		try (Statement stmt = connection.createStatement()) {
			synchronized (databaseLock) {
				// Create or replace the tables
				stmt.executeUpdate(
				        "create or replace table seeds(site VARCHAR(" + Integer.toString(MAX_SITE_LENGTH) + "), visited bit default 0);");
				stmt.executeUpdate("create or replace table sites(site VARCHAR(" + Integer.toString(MAX_SITE_LENGTH) + "));");
				// TODO once the format of jobs is figured out
				//stmt.executeUpdate("create or replace table jobs();");
				stmt.executeUpdate("create or replace table state(site VARCHAR(" + Integer.toString(MAX_SITE_LENGTH) + "));");

				// Insert all seed sites into the 'seeds' table
				try (BufferedReader br = new BufferedReader(new FileReader("seedSites.txt"))) {
					String line;
					while ((line = br.readLine()) != null) {
						stmt.executeUpdate("insert into seeds values('" + line + "', 0);");
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return "initialized new tables";
		} catch (SQLException e) {
			e.printStackTrace();
			return "ERROR: unable to initialize new tables";
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
		// Make sure all threads terminate by not creating new threads
		createThreads = false;

		// Wait for all threads to stop producing new threads
		try {
			Thread.sleep(200);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		synchronized (threads) {
			if (threads.size() == 0) {
				createThreads = true;
				return "ERROR: no threads to pause";
			}

			// Save the state into the database
			synchronized (databaseLock) {
				try (Statement stmt = connection.createStatement()) {
					for (String site : threads.keySet()) {
						if (site.length() <= MAX_SITE_LENGTH) {
							stmt.executeUpdate("insert into state values ('" + site + "');");
						}
					}
				} catch (SQLException e) {
					e.printStackTrace();
					return "ERROR: unable to save state to database";
				}
			}

			// Wait for all threads to terminate
			for (Thread thread : threads.values()) {
				try {
					thread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			threads.clear();
		}

		// Threads are allowed to be created again
		createThreads = true;

		return "paused all threads";
	}

	/**
	 * Restarts all threads in the current database that were
	 * previously paused. If there are no such threads, this
	 * method does nothing.
	 * 
	 * @return a message detailing the effect of this method
	 */
	private String resume() {
		List<String> statePages = new ArrayList<String>();

		// Add all saved sites to statePages, then clear the 'state' table
		synchronized (databaseLock) {
			try (Statement stmt = connection.createStatement()) {
				ResultSet state = stmt.executeQuery("select * from state;");

				while (state.next()) {
					statePages.add(state.getString("site"));
				}

				stmt.executeUpdate("create or replace table state(site VARCHAR(255));");
			} catch (SQLException e) {
				e.printStackTrace();
				return "ERROR: unable to retrieve saved state";
			}
		}

		spawnThreads(statePages);

		if (statePages.size() == 0) return "ERROR: no state was saved";
		else if (statePages.size() == 1) return "resumed 1 thread";
		else return "resumed " + statePages.size() + " threads";
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
	 * Defaults to starting with one thread if the input is null.
	 * 
	 * @param num number of threads to start the web crawling with
	 * @return a message detailing the effect of this method
	 */
	private String start(String num) {
		// Get the number of threads from the input String
		int threadCount;
		if (num == null) {
			threadCount = 1;
		} else {
			try {
				threadCount = Integer.valueOf(num);
				if (threadCount < 1) throw new IllegalArgumentException();
			} catch (NumberFormatException e) {
				return "ERROR: please input a number";
			} catch (IllegalArgumentException e) {
				return "ERROR: please input a number of threads greater than 0";
			}
		}

		// Find seeds to add to the seedPages list
		List<String> seedPages = new ArrayList<String>();
		synchronized (databaseLock) {
			try (Statement stmt = connection.createStatement()) {
				ResultSet availableSeeds = stmt.executeQuery("select * from seeds where visited = 0;");

				// While there are still seeds available and more seeds are still wanted, get a seed,
				// set it to visited in the database, and add it to the seedPages list. Also add the
				// seed to the 'sites' table in the database so that it is not revisited later.
				while (availableSeeds.next() && seedPages.size() < threadCount) {
					String site = availableSeeds.getString("site");
					stmt.executeUpdate("update seeds set visited = 1 where site = '" + site + "';");
					seedPages.add(site);
					stmt.executeUpdate("insert into sites values ('" + site + "');");
				}
			} catch (SQLException e) {
				e.printStackTrace();
				return "ERROR: unable to start threads";
			}
		}

		spawnThreads(seedPages);

		if (seedPages.size() == 0) return "ERROR: no more seeds to start threads from";
		else if (seedPages.size() == 1) return "started 1 thread";
		else return "started " + Integer.toString(seedPages.size()) + " threads";
	}

	/**
	 * Stops all of the threads that are currently running.
	 * Only the threads that have been started by this client
	 * are stopped.
	 * 
	 * @return a message detailing the effect of this method
	 */
	public String stop() {
		// Make sure all threads terminate by not creating new threads
		createThreads = false;

		// Wait for all threads to stop producing new threads
		try {
			Thread.sleep(200);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// Wait for all threads to terminate
		synchronized (threads) {
			if (threads.size() == 0) {
				createThreads = true;
				return "ERROR: no threads to stop";
			}

			for (Thread thread : threads.values()) {
				try {
					thread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			threads.clear();
		}

		// Threads are allowed to be created again
		createThreads = true;

		return "stopped all threads";
	}

	/**
	 * @return a message containing the number of
	 * threads currently running
	 */
	private String threads() {
		int runningThreads = 0;
		synchronized (threads) {
			// Only count threads that are alive
			for (Thread thread : threads.values()) {
				if (thread.isAlive()) runningThreads++;
			}
		}
		if (runningThreads == 1) return "1 thread currently running";
		else return Integer.toString(runningThreads) + " threads currently running";
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
	 * Given a list of URLs, create one thread per URL
	 * where each thread finds new URLs on the page and
	 * finds any existing job postings.
	 * 
	 * @param sites list of URLs
	 */
	private void spawnThreads(List<String> sites) {
		for (String site : sites) {
			if (!createThreads) return;

			Thread tempThread = new Thread(new Runnable() {
				public void run() {
					if (createThreads) {
						// Get all URLs that appear on the specified site
						Elements links = null;
						try {
							Document doc = Jsoup.connect(site).get();
							links = doc.select("a[href]");
						} catch (IOException e) {
							if (createThreads) {
								synchronized (threads) {
									threads.remove(site);
								}
							}
							return;
						} catch (IllegalArgumentException e) {
							if (createThreads) {
								synchronized (threads) {
									threads.remove(site);
								}
							}
							return;
						}

						// Add all sites that have not been visited before to newSites,
						// as well as to the 'sites' table in the database
						List<String> newSites = new ArrayList<String>();
						if (links != null) {
							synchronized (databaseLock) {
								try (Statement stmt = connection.createStatement()) {
									for (Element link : links) {
										String newSite = link.attr("abs:href");
										// Replace any apostrophes to avoid SQL syntax errors
										newSite = newSite.replaceAll("'", "''");
										ResultSet siteFromDB = stmt.executeQuery("select * from sites where site = '" + newSite + "';");
										// If siteFromDB is empty, then newSite doesn't yet exist in the database
										if (!siteFromDB.next() && newSite.length() <= MAX_SITE_LENGTH) {
											newSites.add(newSite);
											stmt.executeUpdate("insert into sites values ('" + newSite + "');");
										}
									}
								} catch (SQLException e) {
									e.printStackTrace();
								}
							}
						}

						// TODO: find job postings

						spawnThreads(newSites);

						// This thread has now completed
						if (createThreads) {
							synchronized (threads) {
								threads.remove(site);
							}
						}
					}
				}
			});

			if (createThreads) {
				synchronized (threads) {
					threads.put(site, tempThread);
				}
			}

			tempThread.start();
		}
	}
}

// TODO: add more seed pages (including a local file that has no links so that sanitizing can be tested)
// TODO: figure out a way to sanitize the database on a set interval
// TODO: should starting seeds be random or in order?

//TODO: find a better way of stopping all threads instead of waiting for 200ms?
// also is it possible to do anything cleaner than the createThreads stopping method?

// TODO commands:
// view - prints all stored job postings
//		- should be options to print a subset of jobs, or to print thread websites...
