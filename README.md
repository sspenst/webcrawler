Job Opening Web Crawler
===

##Description
The Job Opening Web Crawler is a crawler that searches the World Wide Web for job openings. It starts from a certain number of seed pages, collects any job openings that appear on these pages, then searches all of the links reachable from the seed pages. This process continues until the user tells the crawler to stop. The collected data is stored using MariaDB.

##Structure
The user of this program should have a local MariaDB app running, which can be setup through the XAMPP control panel. All important data (meaning all links visited and all job postings collected) is stored in the MariaDB database.

To start this program, the user should run the WebCrawlerServer. This server is multithreaded, so it allows multiple clients to connect to it simultaneously. Once a client is connected to the server, the client can type `help` to get a list of all available commands. Clients execute commands to interact with the database directly, so the server is acting as an arbiter to ensure the clients have safe accesses to the database.