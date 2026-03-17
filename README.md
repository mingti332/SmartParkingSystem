# Smart Parking Sharing and Reservation System

Course design project based on JavaFX + JDBC + MySQL.

## 1. Project Structure

```text
SmartParkingSystem
|- pom.xml
|- sql
|  |- schema.sql
|  |- report_queries.sql
|- docs
|  |- code-module-mapping.md
|- src/main
   |- java/com/parking
   |  |- app
   |  |- config
   |  |- entity
   |  |- dao
   |  |- service
   |- resources
      |- db.properties
```

## 2. Requirements

- JDK 17+
- Maven 3.8+
- MySQL 5.5+ (8.x recommended)

## 3. Quick Start

1. Execute `sql/schema.sql` in MySQL.
2. Edit database credentials in `src/main/resources/db.properties`.
3. Run:

```bash
mvn clean javafx:run
```

## 4. Main Features

- User and role management
- Parking lot management
- Parking space management
- Reservation with time conflict detection
- Parking entry/exit and auto fee calculation
- Payment records and revenue settlement
- Statistics and reports

## 5. Tech Stack

- Java 17
- JavaFX 21
- Maven
- JDBC
- MySQL
- Git

## 6. Default Accounts

- admin / 123456
- owner01 / owner123
- driver01 / driver123

## 7. Notes

- Use UTF-8 for all text files.
- If there is an encoding issue, re-save text files as UTF-8 (without BOM).