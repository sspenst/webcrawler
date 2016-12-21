Job Opening Web Crawler
===

##Description
The Job Opening Web Crawler is a crawler that searches the World Wide Web for job openings. It starts from a certain number of seed pages, collects any job openings that appear on these pages, then searches all of the links reachable from the seed pages. This process continues until the user tells the crawler to stop. The collected data is stored using MariaDB.

##Dependencies
* MariaDB (easy to install with XAMPP)
* MariaDB Connector/J library
* jsoup library

##Structure
The user of this program should have a local MariaDB app running, which can be setup through the XAMPP control panel. All important data (meaning all links visited and all job postings collected) is stored in the MariaDB database.

To start this program, the user should run the WebCrawlerServer. This server is multithreaded, so it allows multiple clients to connect to it simultaneously. When a client is connected to the server, the server automatically connects the client to the database. Clients execute commands to interact with the database directly, and the client can type `help` to get a list of all available commands. All client-database interactions are synchronized to the server so that no operations overlap.

##User Guide
###Basic Usage
* Start the MySQL module through the XAMPP control panel
* Run WebCrawlerServer's main method
* Connect to the server by with `telnet localhost 4949`
* Type `help` to get a list of all available commands
* Initialize the database with tables using `init`
* Commence the webcrawling with `start`
* Run `stop` after some time to stop the webcrawling
* Run some other commands to view results...

###Advanced Usage
* A client can save the crawler's state by using the `pause` and `resume` commands
* Run `use [db]` to use a different database
* Drop a given database with `drop [db]`
* Connect multiple clients to the server to perform:
  * Simultaneous webcrawling on the same database
  * Webcrawling in parallel on different databases