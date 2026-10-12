# 🚗 RidePool - Smarter Ride-Sharing & Fare Comparison

[![GitHub Pages](https://img.shields.io/badge/Live%20Demo-GitHub%20Pages-0d6b57?style=for-the-badge&logo=github)](https://ar0802784.github.io/ridepool/)
[![Java](https://img.shields.io/badge/Java-17%2B-orange?style=for-the-badge&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?style=for-the-badge&logo=springboot)](https://spring.io/projects/spring-boot)
[![Leaflet](https://img.shields.io/badge/Maps-Leaflet%20%2B%20OSM-199900?style=for-the-badge&logo=leaflet)](https://leafletjs.com/)

Find people travelling the same route, split the fare automatically, and instantly discover the cheapest option across **Ola**, **Uber**, **Rapido**, and **inDrive**.

---

## 🌐 Live Web Demo (GitHub Pages)

Experience RidePool live directly in your browser without installing anything:

👉 **[Launch RidePool Live Demo](https://ar0802784.github.io/ridepool/)**

### Key Features in Live Demo:
- 🗺️ **Interactive OpenStreetMap + Leaflet Map**: Tap to set pickup and drop locations, or search anywhere with address autocomplete.
- ⚡ **Real-Time Rider Matching & Simulation**: Find co-riders leaving within 20 minutes and 2 km of your route with live radar search.
- 💰 **Multi-Service Fare Comparison**: Compare fares for Ola Mini cab, Uber Go cab, Rapido Bike taxi, and inDrive with surge pricing and smart splitting.
- 💬 **In-App Messaging with Auto-Replies**: Chat with co-riders with automated conversational replies from simulated riders.
- 📋 **Booking Deep Links & History**: Copy pickup/drop coordinates, launch ride-hailing apps, and view past trips saved across browser sessions.

---

## 🚀 How to Publish to Your GitHub & Activate Your Live Link

Follow these simple steps to post this project on your GitHub account and get a live link:

### Step 1: Initialize Git and Commit
Open PowerShell or Terminal in this folder (`ridepool`):

```bash
git init
git add .
git commit -m "Initial commit: RidePool full-stack application and GitHub Pages live demo"
```

### Step 2: Create a New Repository on GitHub
1. Go to [GitHub.com/new](https://github.com/new).
2. Set **Repository name** to `ridepool`.
3. Set visibility to **Public**.
4. Leave **Initialize with README** **UNCHECKED** (we already have a complete one).
5. Click **Create repository**.

### Step 3: Push Your Code to GitHub
Copy and run the commands shown on GitHub:

```bash
git branch -M main
git remote add origin https://github.com/ar0802784/ridepool.git
git push -u origin main
```

### Step 4: Turn ON GitHub Pages (Get Your Live Link!)
1. Go to your repository on GitHub.
2. Click **Settings** (top right gear icon).
3. In the left sidebar, click **Pages**.
4. Under **Build and deployment** > **Branch**:
   - Select **`main`**.
   - Folder: **`/ (root)`** (or **`/docs`** — both work!).
   - Click **Save**.
5. Wait ~30-60 seconds and refresh the page.
6. GitHub will display your live link at the top:
   > **Your site is live at `https://ar0802784.github.io/ridepool/`** 🎉

---

## 💻 Running the Java Spring Boot Backend Locally

To run the full backend server with Java and Spring Boot:

### Prerequisites
- **JDK 17 or newer**
- **Maven 3.8+**

### Run via Command Line
```bash
mvn spring-boot:run
```

Once started, open [http://localhost:8080](http://localhost:8080) in your browser. The embedded database will initialize automatically in `./data/`.

### Run in an IDE
- **IntelliJ IDEA / Eclipse / VS Code**: Open the folder as a Maven project and run `com.ridepool.RidePoolApplication`.

---

## 🏗️ Project Architecture & How the Pieces Work

| Module | Location | Purpose |
|---|---|---|
| **Live Web App / Demo** | `index.html`, `style.css`, `app.js`, `docs/` | Standalone client application hosted seamlessly on GitHub Pages |
| **Auth & Security** | `com.ridepool.web.AuthController`, `AuthInterceptor` | Session-based authentication with BCrypt password hashing |
| **Matching Engine** | `com.ridepool.service.MatchService` | Matches riders whose pickup & drop are within 2 km and within 20 mins |
| **Fare Estimation** | `com.ridepool.service.FareService` | Multi-provider rate calculations, peak surge, and group fare splitting |
| **Geo & Distance** | `com.ridepool.service.GeoService` | Haversine formula calculation scaled by 1.3 road factor |
| **In-App Chat** | `com.ridepool.service.MessageService`, `ChatController` | In-app messaging thread with co-riders |
| **Thymeleaf UI** | `src/main/resources/templates/` | Server-rendered templates for the Spring Boot application |

---

## 📊 Fare Estimation Logic

Since Ola, Uber, Rapido, and inDrive do not provide public fare APIs, the application calculates realistic estimates using:

$$\text{Fare} = \max(\text{minFare},\, \text{base} + (\text{perKm} \times \text{km}) + (\text{perMin} \times \text{minutes})) \times \text{surge}$$

- **Peak Hours**: 8:00 AM – 10:00 AM & 5:00 PM – 8:00 PM (typical 1.1x - 1.3x surge).
- **Ride Splitting**: Cabs (Ola, Uber, inDrive) split equally up to 4 co-riders. Rapido bike taxis carry 1 rider and are never split.
- **Best Deal**: Flagged automatically based on the lowest price per passenger.

---

## 🛠️ Switching to MySQL (Optional)

To switch from the default H2 database to MySQL:

1. Add the MySQL connector dependency in `pom.xml`:
   ```xml
   <dependency>
       <groupId>com.mysql</groupId>
       <artifactId>mysql-connector-j</artifactId>
       <scope>runtime</scope>
   </dependency>
   ```
2. In `src/main/resources/application.properties`, uncomment the MySQL datasource lines and provide your database credentials.

---

## 📝 License

Distributed under the MIT License. Feel free to use and adapt this project for your own portfolio!
