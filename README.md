**CPEN 221 / Challenge Task 1**
Crawling the Web
===

## Introduction
A **web crawler** is an internet agent (bot) that systematically browses the World Wide Web ([Wikipedia Article](https://en.wikipedia.org/wiki/Web_crawler)). Its purpose is to collect web content to be processed, indexed and used later. The web crawler is one of the major components of *search engines*. It is sometimes called a web spider, or ant.

In the most basic scenario, the web crawler starts at a *root* page, and looks for all the links in the page. The crawler validates the links and then proceeds to visit the links in some order, collecting the links in each visited page, and so on. 

Upon arriving at a page, our crawler will typically

1. Search the page for the desired content. If it finds the content its looking for, then it stores
	* The link identifying the page;
	* The whole HTML content of the page, either to be used for later processing, or for caching purposes.
2. Not revisit a page that has been visited before. This is achieved by storing the visited links in a **database**. (look below for instructions on how to setup you database server.)

##The Task: Harvesting Job Openings
Your task is write a **multithreaded** web crawler in Java *from the scratch* to **collect job postings** announced online. 

Your crawler should start crawling from a set of *seed* pages, spawning a thread for each seed page. Typically the seeds will be company pages, such microsoft.com, qualcomm.com, etc. Your crawler should be able to detect pages that contain job postings. You will do this by using suitable **regular expressions**. *There will be significant creativity in designing your regular expressions*.

Once your crawler detects that a page contains a job posting, it should extract the following information about the job:

1. Company/institution posting the job;
2. Date of job posting;
3. Location;
4. Job title;
5. Job Description;
6. Job posting ID, if available;
7. The link to the job posting.

All of this information should be stored in suitable tables in your database. 

Of course, there is no standard format in which those pieces of information are written across different web pages; therefore, you will have to manually look at online job postings to determine a common pattern in which job postings are written. Your regular expressions should be general enough to handle format discrepancies as much as possible, while at the same time be specific enough to minimize misdetections.

**Question:** How would you uniquely identify each job in your database ? 
     
**Note:** You are **not** allowed to use any external crawling libraries, such as crawler4j.

### Selection Policy: Scheduling page visits
There are two things to consider when visiting pages:

1. The Selection Policy: The order in which the links contained in a page are visited, and
2. Exclusion of already visited pages.

The selection policy could be any of the well-known graph traversal algorithms, such as BFS or DFS. One of your subtasks is to determine the selection policy that best suits our application.

One way to prevent the crawler from visiting a link that has already been visited is to add a flag field in your "links" table in the database (you may call it IS_VISITED), and lookup this field *if the link exists*; i.e., has been added but not visited yet. 

### Synchronization
There will be several threads traversing and indexing pages, and those threads will access the database simultaneously. You will have to synchronize access to your database such that the consistency of the data is preserved. 

Moreover, even if each thread maintains its own graph, say, of links scheduled to be visited or the ones visited already, it might be the case that another thread traverses through links that lead to a page in another thread's graph. *In this assignment, only one thread can be working on a page at a time.* That is, we require that a page be *locked* during the time a thread is working on it; if a thread is working on a certain page and another thread attempts to visit the same page, then this other thread will be blocked. In this situation, a reasonable course of action is to allow the blocked thread to access another page. **But how would a blocked thread choose another page to visit next? Randomized fashion ? Or the next page in the BFS queue, say ?** *It is your task to implement a reasonable policy.*


###Crawler Startup/Termination

At startup, your crawler should select a subset of the pages not visited so far, and spawn a thread for each page. A spawned thread will then go on to traverse the web and build its "page" *graph*. One way to choose the initial seeds pages is to pick them randomly from the database. Other schemes are possible, and you are free to decide how to do it.

Your should provide the ability for the user to terminate the crawler at any time. When your crawler receives a command to terminate, it should properly shut down all threads gracefully, and ensure that the database is in a consistent state. If a thread is working on a page, then it should store the state of the page it was working on (finished, processing, etc). Moreover, all links that are scheduled to be visited should of course be stored in the database so that crawling can be resumed from where it stopped.


###Job Posting Edit/Removal
What if a job posting that is already in your database becomes unavailable online? Also any of the job attributes mentioned above might change online. This has to be reflected in your database as soon as possible to avoid having stale information. You are asked to implement a policy to *refresh* your stored job postings. One way is to create a *periodic* (multithreaded?) job that sanitizes your database.   


##Setting up your Environment
You will use a database to handle the storage component of the crawler. You will use **MariaDB** to create a relational database and store the crawled web content in tables.

[MariaDB](https://mariadb.org) is a new replacement for the infamous MySQL. It is open source, and it significantly extends the functionality of MySQL without sacrificing compatibility. The easiest way to install MariaDB is through ApacheFriends' [XAMPP](https://www.apachefriends.org/index.html) installer.  XAMPP installs many applications things related to web development, such as Apache server, PHP, MariaDB, etc. One of the most useful applications that come with XAMPP is **phpMyAdmin**, which is a graphical (browser-based) user interface that allows you to access MariaDB and perform all database-related operations, such as database creation/drop, table creation/drop, database/table import/export, etc. Once you install XAMPP and start the database server (through XAMPP's GUI), you will be ready to create a database and the associated tables.

To access the database from your application, you will have to install MariaDB Connector/J. Use [this download link](https://mariadb.com/kb/en/mariadb/about-mariadb-connector-j/). Once MariaDB Connector/J is installed, you will use it as the database connector (i.e., import `org.mariadb.jdbc.Driver`, not `com.mysql.jdbc.Driver`). Read the instructions in the link above carefully.

Finally, you will use JDBC to create SQL statements from your Java program and issue queries to your database. Read [this lesson](https://docs.oracle.com/javase/tutorial/jdbc/basics/) on JDBC to get you started quickly.



##Testing

You should develop testing strategies to ensure at least the following:

1. Proper synchronization of database access; i.e., that no one thread overwrites the data written by another thread, and no two or more threads access the same page (or a job's entry in the database) at the same time; 
2. Proper synchronization of thread page visits. *In particular, you need to come up with a scenario where at least two threads attempt to access the same page simultaneously*;  
3. Your crawler is able to extract as many job postings as possible, and once a job is found, that your crawler is able to successfully extract as much of the job information mentioned above as possible; 
4. You have to test your crawler with 5, 20, 100, and 1000 threads. Devise a scheme to measure the performance of your crawler as the number of threads grows.
5. Demonstrate that job sanitization works as expected. At the least, you have to create a webpage with some job postings, and then edit/remove some jobs postings and let your application run periodically and reflect all the updates in the database.  
