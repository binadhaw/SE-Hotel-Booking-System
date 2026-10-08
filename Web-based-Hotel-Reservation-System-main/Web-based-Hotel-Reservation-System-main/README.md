# Web-based Hotel Reservation System for Tourists

SE2030 Software Engineering project (2026-Y2-S1-MLB-B3G1-06) - a Spring Boot web application where tourists
search and book hotels across Sri Lanka, hotel managers run their properties, travel agents plan itineraries
and administrators oversee the platform.

## Features by role

| Role | What they can do | Backlog items |
|------|------------------|---------------|
| **Visitor** | Browse and search hotels, read reviews, view agent profiles, register, reset password | PBI-09, 10, 11, 12 |
| **Tourist** | Search by location, price, facilities, dates and party size; book one or several rooms with a coupon or discount; **pay online by card for instant confirmation** (or bank transfer); edit/cancel bookings; see things to do near each upcoming stay; review a stay; ask a hotel a question (with priority); request and refine an itinerary from a travel agent; travel preferences for targeted offers and recommendations | PBI-01 - 08 |
| **Hotel manager** | Register hotels (approved by admin), manage rooms, photos, pricing **and staff**, approve/decline bookings, handle refunds, reply to inquiries and reviews, add nearby attractions, run hotel offers and email coupons, export bookings | PBI-17, 18, 19, 20 |
| **Travel agent** | Public profile with sample itineraries, recommend local experiences, send versioned itinerary proposals with attractions, performance dashboard | PBI-21, 22, 23, 24 |
| **Administrator** | Dashboard with KPIs and charts, system health (response times, errors, uptime, database), activity log, approve/reject hotels, safely remove hotels, verify agents, suspend/delete users, moderate reviews, platform-wide offers, CSV reports | PBI-13, 14, 15, 16 |

Everyone gets an in-app notification centre (navbar bell) and email notifications for important events.

## Tech stack

Java 17+, Spring Boot 3.3 (Web, Data JPA, Security, Validation, Mail, Thymeleaf), MySQL 8, Bootstrap 5.
Tests use JUnit 5, Spring Security Test and an in-memory H2 database.

## Getting started

1. **MySQL** - have a MySQL 8 server on `localhost:3306`. The `hotel_db` schema and all tables are created
   automatically on first start.
2. **Secrets** - copy `secrets.properties.example` to `src/main/resources/secrets.properties` and fill in your
   MySQL password and an encryption key (`openssl rand -base64 32`). That file is git-ignored, so passwords and keys
   never reach GitHub. Environment variables (`DB_USERNAME`, `DB_PASSWORD`, `CRYPTO_KEY`) override it.
3. **Run**
   ```
   mvnw spring-boot:run
   ```
   Open http://localhost:8080

### Demo accounts (created on first start)

| Username | Password | Role |
|----------|----------|------|
| `admin` | `admin123` | Administrator |
| `manager` | `manager123` | Hotel manager |
| `agent` | `agent123` | Travel agent |
| `user` | `user123` | Tourist |

The demo accounts' security answer is `demo`.

**Demo hotels:** on a database with no hotels, six showcase properties (Galle, Mirissa, Ella, Sigiriya,
Nuwara Eliya, Arugam Bay) with rooms and photos are created for the `manager` account. Set `DEMO_DATA=false`
to start with an empty catalogue.

**Online payments** use **AuraPay**, a built-in demo gateway with the redirect flow real providers use: the guest reviews the order, is redirected to the hosted AuraPay page, enters the card, confirms a one-time code (shown on the page as a "demo bank SMS") and is redirected back with the result. No real money moves. Pay with `4242 4242 4242 4242`, any
future expiry and any CVV (the checkout has one-tap test-card buttons). Declines can be simulated with `4000 0000 0000 0002` (declined), `4000 0000 0000 9995` (insufficient funds), `4000 0000 0000 0127` (wrong CVV), `4000 0000 0000 0069` (expired) and `4000 0000 0000 0119` (bank error). After 5 failed attempts in 15 minutes the checkout pauses for that user (fraud protection). Only the card brand and last four
digits are stored. Coupons `SAVE10` (10%) and `WELCOME20` (20%) and a set of well-known
Sri Lankan attractions are also seeded. Hotels that existed before hotel ownership was introduced are assigned
to the `manager` account.

### Email

By default emails are only written to the log (`app.mail.enabled=false`), so no mail server is needed.
To send real email (e.g. through Gmail with an app password):

```
set MAIL_ENABLED=true
set MAIL_HOST=smtp.gmail.com
set MAIL_PORT=587
set MAIL_USERNAME=you@gmail.com
set MAIL_PASSWORD=your-app-password
set MAIL_SMTP_AUTH=true
set MAIL_STARTTLS=true
```

### Data protection & HTTPS

Personal data (phone numbers, card holder names, inquiry and trip-request contact details, staff contacts) is
encrypted in the database with AES-256-GCM, using the key from `secrets.properties` (or `CRYPTO_KEY`).
Keep that key: data encrypted with one key cannot be read with another. Existing plain-text values are encrypted
automatically on start-up. Passwords and security answers are BCrypt hashes.

To run over HTTPS, create a certificate once and start with the `https` profile (port 8443):

```
keytool -genkeypair -alias hotel -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore hotel-keystore.p12 -validity 365 -dname "CN=localhost"
set SSL_KEYSTORE_PASSWORD=<the password you chose>
mvnw spring-boot:run -Dspring-boot.run.profiles=https
```

## Running the tests

```
mvnw test
```

The tests run against an in-memory H2 database (`src/test/resources/application.properties`) and never touch MySQL.

## Key business rules

- **Availability is date-based.** An approved booking blocks its room only for the nights it covers
  (check-in up to, not including, check-out). Pending requests do not block a room; approving a booking
  that overlaps another approved one is refused.
- **No double booking, even at the same instant.** Every confirmation (card payment, manager approval, an edit
  that stays approved) locks the room's database row before re-checking for a clash, so when several guests
  confirm the same room at once exactly one succeeds and the others are not charged.
- **Multi-room bookings** share a group reference; the party is spread over the rooms by capacity.
- **Coupons** are validated against the Promotions table and can be limited to one hotel. A coupon and a
  standard discount are never combined - the better one applies.
- **Removing a hotel never deletes booking history.** Open bookings are cancelled (paid ones go to refund)
  and the guests are notified.
- **Reviews** require a completed, approved stay; one review per booking.
- **Ownership** - managers only see and change their own hotels, rooms, bookings, inquiries and offers.

## Design patterns & documentation

| Document | Contents |
|---|---|
| `docs/DESIGN_PATTERNS.md` | Strategy (discount pricing), Observer (domain events -> notifications & audit log), Template Method (photo storage): where, why, benefits, how to demo |
| `docs/DIAGRAMS.md` | ER diagram, domain class diagram, design-pattern class diagram, booking state diagram, use case diagrams, one activity diagram per major function (Mermaid) |
| `docs/CRUD_AND_VALIDATION.md` | CRUD matrix per module and every server-side validation rule |
| `docs/ETHICAL_CONSIDERATIONS.md` | Data privacy, consent, fairness, accountability and accessibility measures (design document section) |
| `docs/REQUIREMENTS_TRACEABILITY.md` | Every proposal requirement -> where it is implemented -> how it was verified (tests, end-to-end run, load test) |

## Project structure

```
src/main/java/com/Reservation/Hotel
  config/       security, password encoder, demo data seeder, static uploads
  events/       domain events + listeners (Observer pattern)
  pricing/      discount strategies (Strategy pattern)
  payment/      card checks, payment gateway interface + demo gateway, payment service
  security/     field encryption (AES-GCM converter) and data-protection start-up tasks
  controller/   web controllers (one per feature area)
  dto/          form and search objects
  model/        JPA entities
  repository/   Spring Data repositories
  service/      business logic
src/main/resources/templates   Thymeleaf pages (fragments/ holds shared navbar, alerts, reviews...)
uploads/                       hotel & room photos and payment receipts (created at runtime)
```

See `UPGRADE_ROADMAP.md` for the phase-by-phase upgrade plan that brought the project in line with the proposal.
