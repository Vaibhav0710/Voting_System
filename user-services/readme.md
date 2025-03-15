# 🗳️ Voting System - User Service

This is the **User Service** for the Voting System built with **Spring Boot**, **MySQL**, and follows **SOLID principles
**.

## 📌 Features

- User Registration with role-based data.
- User Login with validation.
- Uses DTOs for data transfer and separation of concerns.
- Repository layer for database operations.
- Service layer for business logic.

## 🏗️ Tech Stack

- **Spring Boot** (Backend Framework)
- **MySQL** (Database)
- **Lombok** (For boilerplate reduction)
- **Jakarta Validation** (For input validation)

## 📂 Folder Structure

```
user-service/
│── src/
│   ├── main/java/com/voting/userservices/
│   │   ├── controller/       # REST controllers
│   │   ├── dto/              # Data Transfer Objects (DTOs)
│   │   ├── entities/         # JPA Entity models
│   │   ├── repository/       # Database repositories
│   │   ├── service/          # Business logic services
│── src/main/resources/
│   ├── application.properties  # Database configuration
│── pom.xml                   # Maven dependencies
│── README.md                 # Documentation
```

## 🚀 How to Run

1. **Clone the Repository**
   ```sh
   git clone https://github.com/Vaibhav0710/Voting_System
   cd user-service
   ```

2. **Configure MySQL Database**  
   Update `src/main/resources/application.properties` with your MySQL credentials.

3. **Build and Run the Application**
   ```sh
   mvn spring-boot:run
   ```

4. **API Endpoints**
    - `POST /api/users/register` - Register a new user.
    - `POST /api/users/login` - Login user.

---
👨‍💻 Developed for a **Blockchain-inspired Voting System**.
