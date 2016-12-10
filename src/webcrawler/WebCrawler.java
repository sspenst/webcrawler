package webcrawler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class WebCrawler {
	public WebCrawler() {
		// TODO: something
	}
	
	public String request(String request) {
		return request;
	}
	
	public static void main(String[] args) {
		try {
			// "test" is the database name
			Connection connection = DriverManager.getConnection("jdbc:mariadb://localhost:3306/test");
			Statement stmt = connection.createStatement();
			stmt.executeUpdate("CREATE TABLE a (id int not null primary key, value varchar(20))");
			stmt.close();
			connection.close();
			System.out.println("WE MADE IT");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}

/*
 * TODO: figure out what SQL commands I need to use to store the data I need for the webcrawler
 * TODO: is the database threadsafe? i don't think so. make sure accesses to connection are synchronized
 * TODO: read the JDBC basics tutorial to figure out how to properly use SQL commands in Java
 */

/*
WEBCRAWLER IS A SERVER
clients (or maybe just one client?) can connect to the webcrawler server and
can start/stop/restart the server, and can run commands on the server? to get data?
create a refresh command which can be executed by a user, as well as is automatically
executed periodically by the crawler (every 10 seconds? minute?)

TODO: need a list of like 100s of seed pages
TODO: what happens if all of the threads running can't find any more links?
i.e. all of the links on the current pages have already been visited, so there is nowhere to go
just print nothing else can be found I guess "Dead end reached..."

commands:
help - this text
open [db] - create a connection to database db (default is db)
	- calls close before connecting
close - closes the current connection
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