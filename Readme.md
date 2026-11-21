# Wildpath Safari Booking
Wildpath lets travelers browse safari tours and vehicles, make bookings, and pay online.

# Tech Stack
Frontend: React/Vite user interface for browsing tours, vehicles, bookings, payments, and reviews. It runs on port 5173 and proxies /api requests to the backend.

Backend: Java/Spring Boot REST API that handles authentication, bookings, payments, notifications, and business logic. It runs on port 8080.

Database: PostgreSQL stores application data in production, with persistent Docker storage. End-to-end tests use an in-memory H2 database.

# Strucure diagram
```text
fullstack-app/
├── frontend/
│   ├── public/
│   ├── src/
│   │   ├── components/
│   │   ├── App.tsx
│   │   └── api.ts
│   ├── test/
│   ├── e2e/
│   └── server.js
├── src/
│   ├── main/
│   │   ├── java/com/example/safari/
│   │   │   ├── admin/
│   │   │   ├── booking/
│   │   │   ├── car/
│   │   │   ├── config/
│   │   │   ├── notification/
│   │   │   ├── operator/
│   │   │   ├── payment/
│   │   │   ├── review/
│   │   │   ├── security/
│   │   │   ├── tour/
│   │   │   ├── tracker/
│   │   │   └── SafariBookingApplication.java
│   │   └── resources/
│   └── test/
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

# Running the application

With Docker, configure the required environment variables and run:
Docker: From the project root, run `docker compose up --build`. It starts PostgreSQL, Spring Boot on port 8080, and the frontend on 5173.
`docker-compose down -v`
`docker-compose up --build -d`
`docker compose logs -f`

Local development: Run the backend with `mvn spring-boot:run` and the frontend from frontend/ using `npm install`, then `npm run dev`.

Tests: Backend—`mvn test`; frontend unit—`npm test`; end-to-end—`npm run test:e2e` in frontend/

# Database

Production: PostgreSQL, managed by Spring Data JPA.
Docker: PostgreSQL on port `5432`, with persistent `safari_postgres_data` storage.
Tests: In-memory H2 database.

## ER diagram (DBML)

Paste this schema into [dbdiagram.io](https://dbdiagram.io) to generate the ER diagram.

```dbml
// Use DBML to define your database structure
// Docs: https://dbml.dbdiagram.io/docs

Table app_users {
  id bigint [pk, increment]
}

Table tours {
  id bigint [pk, increment]
}

Table departures {
  id bigint [pk, increment]
  tour_id bigint [not null]
}

Table bookings {
  id bigint [pk, increment]
  customer_id bigint [not null]
  departure_id bigint [not null]
}

Table booking_optional_experiences {
  booking_id bigint [not null]
  selection_order int [not null]

  indexes {
    (booking_id, selection_order) [pk]
  }
}

Table cars {
  id bigint [pk, increment]
}

Table car_bookings {
  id bigint [pk, increment]
  car_id bigint
  customer_id bigint
}

Ref: tours.id < departures.tour_id
Ref: app_users.id < bookings.customer_id
Ref: departures.id < bookings.departure_id
Ref: bookings.id < booking_optional_experiences.booking_id
Ref: cars.id < car_bookings.car_id
Ref: app_users.id < car_bookings.customer_id
```


# EMail Password
With $env:MAIL_PASSWORD='kyzxadoqjdunnmxn'


# Cloudflare

Cloudflare provides DNS, HTTPS, security, and optional CDN proxying for the public domain safaritours.dpdns.org. It can route visitors to the application while helping block malicious traffic and improving delivery speed. The application already configures this domain for canonical URLs, sitemap links, CORS, and frontend/backend deployment references. Run `cloudflared tunnel run safaritours-tunnel`

# Google Search Console

Google Search Console is a Google tool for monitoring and improving a website’s visibility in search results. It verifies ownership of `safaritours.dpdns.org`, tracks indexing status and search performance, detects crawl or technical issues, and submits `https://safaritours.dpdns.org/sitemap.xml` to help Google discover the application’s pages.