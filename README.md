# **Transport Pass Management System**

A desktop application built with JavaFX and connected to a MySQL database to manage public transport passes. This project is designed to support sustainable urban mobility (in line with **SDG 11: Sustainable Cities & Communities**) by providing a simple, modern tool for creating, tracking, and analyzing pass validity and usage.

## **Features**

* **Secure User Authentication:** A full login and registration system. Passwords are securely hashed using SHA-256 before being stored in the database.  
* **CRUD Operations:** Full Create, Read, Update, and Delete functionality for all transport passes.  
* **Live Dashboard & Analytics:**  
  * A summary panel showing real-time counts of **Total Passes**, **Active Passes**, and passes **Expiring Soon** (within 5 days).  
  * A **Pie Chart** that visualizes the distribution of different pass types (e.g., Bus, Metro, Train).  
* **Dynamic Search and Filtering:**  
  * Instantly search the pass directory by passenger name, pass type, or status.  
  * Filter the list to show "All," "Active," or "Expired" passes.  
* **Route & Duration Management:**  
  * Specify source and destination for each pass (pre-filled with common Mumbai locations).  
  * Automatically calculates the pass's Valid Until date based on the selected duration (Daily, Weekly, Monthly, etc.).  
* **Responsive UI:** A clean, multi-column layout that organizes data entry, the main pass directory, and analytics panels.

## **Technologies Used**

* **Java (JDK 11+):** The core programming language.  
* **JavaFX (OpenJFX 17+):** Used for building the modern graphical user interface.  
* **MySQL:** The relational database used to store user and pass data.  
* **JDBC (MySQL Connector/J):** Used for connecting the Java application to the MySQL database.

## **Setup and Installation**

Follow these steps to get the project running on your local machine.

### **1\. Prerequisites**

* **Java JDK 11** or newer.  
* **JavaFX SDK 11** or newer. You can download it from [GluonHQ](https://gluonhq.com/products/javafx/).  
* **MySQL Server 8.0** or newer.  
* **MySQL Connector/J:** The JDBC driver for MySQL. You can download the .jar file from the [official MySQL website](https://dev.mysql.com/downloads/connector/j/).

### **2\. Database Setup**

1. Ensure your MySQL server is running.  
2. Create a new database for the project. You can use the following SQL command:  
   CREATE DATABASE transport\_db;

3. **No need to create tables\!** The application is designed to automatically create the transport\_pass and auth\_user tables if they don't exist on the first run.

### **3\. Configure Database Credentials**

This is the most important step. You must update the hardcoded database credentials in the Java file.

1. Open TransportPassSystem.java.  
2. Navigate to **lines 33-35** (approximately).  
3. Change the DB\_URL, DB\_USER, and DB\_PASSWORD values to match your local MySQL setup.  
   // BEFORE  
   private static final String DB\_URL \= "jdbc:mysql://localhost:3306/transport\_db";  
   private static final String DB\_USER \= "Rahul9969";  
   private static final String DB\_PASSWORD \= "Rahul@9969";

   // AFTER (example)  
   private static final String DB\_URL \= "jdbc:mysql://localhost:3306/transport\_db";  
   private static final String DB\_USER \= "your\_mysql\_username"; // e.g., "root"  
   private static final String DB\_PASSWORD \= "your\_mysql\_password";

## **Running the Application**

### **Option 1: Running from an IDE (Recommended)**

1. Open the project in your favorite Java IDE (e.g., IntelliJ IDEA, Eclipse).  
2. Add your downloaded **JavaFX SDK** and **MySQL Connector/J .jar file** to the project's libraries/build path.  
3. **Configure VM Options:** JavaFX applications run as modules. You need to tell the JVM where to find them. In your IDE's "Run Configuration" for the TransportPassSystem class, add the following VM options:  
   \--module-path /path/to/your/javafx-sdk-17/lib \--add-modules javafx.controls,javafx.graphics

   *(Remember to replace /path/to/your/javafx-sdk-17/lib with the actual path on your computer.)*  
4. Run the main method in TransportPassSystem.java.

### **Option 2: Running from the Command Line**

1. Place the mysql-connector-j-8.x.x.jar file in the same directory as your .java file.  
2. **Compile the code:**  
   javac \--module-path /path/to/your/javafx-sdk-17/lib \--add-modules javafx.controls,javafx.graphics \-cp mysql-connector-j-8.x.x.jar TransportPassSystem.java

   *(Adjust paths and JAR file name as needed)*  
3. **Run the application:**  
   java \--module-path /path/to/your/javafx-sdk-17/lib \--add-modules javafx.controls,javafx.graphics \-cp "mysql-connector-j-8.x.x.jar:." TransportPassSystem

   *(Note: On Windows, use a semicolon ; instead of a colon : in the classpath \-cp)*