# eQi – Appointment Booking System

[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-21-DD0031?logo=angular&logoColor=white)](https://angular.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Full-stack appointment booking system for physiotherapy clinics — patient self-service scheduling, professional agenda management, and admin oversight, built with real-world clinic operations in mind.

---

## Live Demo

- **Frontend (app):** https://turnero-kinesiologia-murex.vercel.app
- **Backend API:** https://turnero-backend-vmme.onrender.com/api/services (public endpoint)

> **Note:** The backend runs on Render's free tier, which suspends instances after 15 minutes of inactivity. The first request after an idle period may take 30–60 seconds while the service spins back up (cold start). Subsequent requests are fast.

### Demo credentials

A seeded patient account is available for quick testing without registration:

| Email | Password | Role |
|-------|----------|------|
| `juan.perez@example.com` | `changeme123` | PATIENT |

These are dev-profile seed data credentials intended for evaluation purposes.

## Deployment

| Layer | Service | Details |
|-------|---------|---------|
| **Frontend** | [Vercel](https://vercel.com) | Angular SPA, built with `build.sh` |
| **Backend** | [Render](https://render.com) | Dockerized Spring Boot (multi-stage Dockerfile) |
| **Database** | [Neon](https://neon.tech) | Serverless PostgreSQL |

The frontend cannot hardcode the backend URL because it differs between local development and production. Instead, `environment.prod.ts` contains a `__API_URL__` placeholder that is replaced at build time by `frontend/build.sh` using `sed` with the `API_URL` environment variable. Vercel is configured to run `cd frontend && bash build.sh` as its build command, and `API_URL` is set in Vercel's environment variables to point to the Render backend URL.

## Screenshots

_Coming soon — will include the booking wizard, professional agenda view, and admin dashboard._

---

## Context

This is not a tutorial project. eQi was built for **eQi – Especialidades Kinésicas**, a real physiotherapy clinic in Neuquén, Argentina, with the intent that it will be used in day-to-day operations. The system handles the clinic's actual constraints: two physical treatment boxes, recurring equipment reservations (an EMSELLA machine that occupies a box on fixed schedules), professionals who each offer specific services, and a receptionist/admin who needs full visibility.

The clinic's operational reality drives the technical complexity. A single box can hold one patient at a time, but the clinic has two boxes — except when the EMSELLA machine is in use, in which case one box is permanently reserved for EMSELLA sessions and only the other box is available for general appointments. This isn't a simple "max 2 concurrent" rule; it's a capacity model where the effective pool shrinks based on which recurring blocks overlap a given time slot. The system must also respect that different services have different durations (60 minutes for most services, 30 minutes for EMSELLA), and a professional can only be booked for services they actually offer.

The dual goal is portfolio demonstration and practical utility. Every design decision — from the IDOR protection strategy to the recurring-block data model — was made because the problem demanded it, not because a tutorial said to.

---

## Features

### Patient
- Register an account and manage profile
- Book appointments through a **4-step mobile-first wizard** (service → professional → date → time slot) that respects per-service duration and per-professional availability
- View and cancel upcoming appointments (cancellation blocked within 24 hours of the appointment)
- Conditional step skipping: if only one professional offers a service, the wizard skips the professional-selection step automatically
- Graceful conflict handling: if another patient books the same slot between selection and confirmation, the wizard shows a clear message and re-fetches available slots

### Professional
- View daily agenda with appointment details and status
- Confirm, complete, mark no-show, or cancel appointments
- See clinic-wide occupancy for the selected day (anonymized — no patient data from other professionals' appointments)

### Admin
- Full visibility into all appointments across all professionals and dates
- Monthly agenda overview with per-day appointment counts
- Same action capabilities as professionals (confirm, complete, cancel, no-show)
- Bootstrap admin account created on first startup via environment variables

### System-wide
- JWT authentication with role-based authorization (PATIENT, PROFESSIONAL, ADMIN)
- IDOR protection: patients cannot book on behalf of other patients (the `patientId` is never accepted from the client)
- Appointment state machine with enforced transitions (the compiler catches exhaustiveness gaps when new statuses are added)
- Clinic capacity enforcement: maximum 2 concurrent appointments (configurable), accounting for recurring block reservations
- Privacy-aware schedule sharing: professionals see occupancy but not other professionals' patient data
- Dev profile seeds real clinic data (services, professionals, availability, recurring blocks) so the app is immediately usable after startup

---

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| **Backend** | Java | 21 |
| **Framework** | Spring Boot (Web, Data JPA, Security, Validation, Flyway) | 4.1 |
| **Database** | PostgreSQL | 16 |
| **Migrations** | Flyway | (via Spring Boot starter) |
| **JWT** | jjwt (api, impl, gson) | 0.12.6 |
| **Frontend** | Angular (standalone components, signals) | 21 |
| **UI** | Angular Material + custom brand theme | 21 |
| **Styling** | SCSS, mobile-first | — |
| **Frontend Testing** | Vitest | 4.x |
| **Backend Testing** | JUnit 5, Testcontainers, MockMvc | — |
| **Local Infra** | Docker Compose (PostgreSQL) | — |

---

## API Overview

The backend exposes a REST API under `/api`. Key endpoints:

| Method | Endpoint | Auth | Roles | Description |
|--------|----------|------|-------|-------------|
| `POST` | `/api/auth/register` | Public | — | Patient registration (role forced to PATIENT) |
| `POST` | `/api/auth/login` | Public | — | Returns JWT token |
| `GET` | `/api/auth/me` | Yes | Any | Current user info |
| `POST` | `/api/auth/change-password` | Yes | Any | Change password (required after admin bootstrap) |
| `GET` | `/api/services` | Public | — | List all active services |
| `GET` | `/api/services/{id}/professionals` | Public | — | Professionals offering a service |
| `POST` | `/api/appointments` | Yes | PATIENT | Book an appointment |
| `GET` | `/api/appointments/my` | Yes | PATIENT | Patient's own appointments |
| `GET` | `/api/appointments/available-slots` | Public | — | Available slots for a professional/service/date |
| `GET` | `/api/appointments/my-agenda` | Yes | PROFESSIONAL | Professional's daily agenda |
| `GET` | `/api/appointments/agenda` | Yes | PROFESSIONAL, ADMIN | Clinic-wide day agenda |
| `GET` | `/api/appointments/agenda/month` | Yes | PROFESSIONAL, ADMIN | Monthly appointment counts |
| `POST` | `/api/appointments/{id}/cancel` | Yes | Owner | Cancel (patient: 24h minimum) |
| `POST` | `/api/appointments/{id}/confirm` | Yes | PROFESSIONAL, ADMIN | Confirm appointment |
| `POST` | `/api/appointments/{id}/complete` | Yes | PROFESSIONAL, ADMIN | Mark as completed |
| `POST` | `/api/appointments/{id}/no-show` | Yes | PROFESSIONAL, ADMIN | Mark as no-show |

---

## Architecture and Design Highlights

### Layered backend with DTO isolation

The backend follows a standard Controller → Service → Repository layering, but with a strict rule: JPA entities never leave the service layer. Every API response is a dedicated DTO (`AppointmentResponse`, `AvailableSlotResponse`, `MonthSummaryResponse`, etc.) constructed by explicit mapper methods. This prevents accidental entity serialization, avoids lazy-loading surprises in JSON output, and makes the API contract independent of the persistence model. Adding a field to the database schema does not automatically leak into the API — it requires an intentional change to the mapper.

### Shared capacity logic for booking and availability display

The `checkCapacity` method is the single source of truth for whether a time slot is bookable. It is called by both `bookAppointment` (which rejects invalid bookings) and `findAvailableSlots` (which populates the patient-facing wizard). This eliminates an entire class of bugs where the UI shows a slot as available but the backend rejects it, or vice versa.

The method accounts for recurring blocks: each overlapping block reduces the general capacity pool by one, and services that match a block's dedicated service (e.g., EMSELLA during its reserved window) are routed to a separate capacity check against that specific box. This two-path logic — general capacity vs. block-dedicated capacity — models the clinic's physical reality: the EMSELLA machine has its own box, so EMSELLA appointments don't compete with general appointments for the remaining capacity.

### Overlap logic tested against real Postgres

The overlap and capacity queries use PostgreSQL-specific functions (`timestampadd`, `EXTRACT(DAY FROM ...)`) that behave differently from H2's emulation. All 111+ integration tests run against a real PostgreSQL 16 instance spun up via Testcontainers. This means the tests validate the actual SQL that will run in production, not a database-independent approximation.

The trade-off is slower test execution (each test class starts its own container); the gain is confidence that the capacity and slot logic is correct against the real query planner, the real timestamp handling, and the real `timestampadd` semantics. H2 would silently emulate these functions with subtly different behavior, which could mask bugs in exactly the code that matters most.

### Recurring blocks as configurable data, not hardcoded rules

The EMSELLA machine's fixed schedule and a professional's recurring RPG sessions are modeled as `RecurringBlock` rows in the database — each with a day of week, time range, optional service, optional professional, and an `active` flag. Admins can add, modify, or deactivate blocks without code changes.

This was a deliberate decision: the clinic's operational patterns change (new equipment, schedule shifts, a professional changing their availability), and hardcoded capacity rules would require a redeployment for every adjustment. The `RecurringBlock` model also extends naturally to future scenarios — a new piece of equipment, a visiting professional with fixed hours, or a room reservation that isn't tied to a specific service.

### Structural IDOR protection

The `CreateAppointmentRequest` DTO deliberately omits `patientId`. The patient identity is resolved server-side from the JWT principal in `AppointmentService.bookAppointment`, so it is physically impossible for a patient to submit a booking under another patient's identity. This is not a check — it is an architectural constraint. The field does not exist in the wire format, so there is nothing to validate or reject.

Where the ID is unavoidable (e.g., viewing an appointment by ID), ownership is verified explicitly before returning data or allowing state transitions. The `requireOwnership` method in `AppointmentService` checks that the authenticated user matches the appointment's owner, and the `findOwnedForTransition` method combines the lookup with the ownership check in a single call.

### Server-enforced role assignment

Public registration always creates a PATIENT user, regardless of what the HTTP body contains. The `RegisterRequest` DTO has no `role` field; if a malicious client sends `"role": "ADMIN"` in the JSON, Jackson silently ignores it. There is an integration test (`registerSendingAdminRoleInBodyStillCreatesPatient`) that explicitly verifies this behavior by sending `"role": "ADMIN"` in the request body and asserting the created user has role PATIENT.

The admin account is bootstrapped from environment variables on startup by `AdminBootstrapRunner`, and the application refuses to start outside the dev profile if `ADMIN_EMAIL` or `ADMIN_PASSWORD` are not set. This prevents deploying to production with a missing or default admin account.

### Explicit appointment state machine

`AppointmentStatus` defines an explicit `canTransitionTo` method that encodes which status transitions are valid: `BOOKED` can transition to `CONFIRMED`, `CANCELLED`, `COMPLETED`, or `NO_SHOW`; `CONFIRMED` can transition to `CANCELLED`, `COMPLETED`, or `NO_SHOW`; and `CANCELLED`, `COMPLETED`, and `NO_SHOW` are terminal states.

Calling code uses `ensureTransitionAllowed`, which throws if the transition is invalid. When a new status is added to the enum, the compiler enforces that the transition logic is updated — the transition map must account for the new value or the build fails. Role-based restrictions (patients can only cancel, professionals can confirm/complete/no-show, admins can cancel without time restrictions) are layered on top of the transition rules in the controller and service layers.

### Privacy-aware clinic agenda

When a professional views the clinic-wide agenda, they see all appointments for the day — but appointments belonging to other professionals are returned with patient data omitted. This is enforced at the DTO level (`AgendaEntryResponse` conditionally includes patient fields based on whether the current user owns the appointment), not by frontend filtering. No patient information leaves the server unnecessarily.

This is a privacy-by-design approach: even if the frontend were modified (or a different client consumed the API), the server would not expose patient data to professionals who have no relationship with those appointments.

### Reactive Angular frontend

The frontend uses Angular signals for state management, functional interceptors for JWT injection, and functional route guards for access control. The booking wizard dynamically adjusts its step count based on the number of professionals offering the selected service — if only one professional offers it, the professional-selection step is skipped entirely via a `computed` signal.

The layout adapts between horizontal and vertical stepper orientations at a 640px breakpoint, and the agenda view uses a day-detail component that re-fetches on date selection via an `effect()` tied to the selected date signal. The architecture avoids class-based lifecycle complexity in favor of composable, declarative state.

### Frontend route protection

Routes are protected by composable functional guards:
- `authGuard` — redirects unauthenticated users to login
- `roleGuard('PATIENT')` — restricts booking and my-appointments to patients
- `roleGuard('PROFESSIONAL', 'ADMIN')` — restricts the agenda view to professionals and admins
- `mustChangePasswordGuard` — intercepts users who must change their password (admin bootstrap flow) and redirects to the change-password page before allowing access to any other route

---

## Getting Started

### Prerequisites

- **JDK 21** (e.g., [Adoptium](https://adoptium.net/))
- **Node.js 20+** and Angular CLI (`npm install -g @angular/cli`)
- **Docker** (required for PostgreSQL and Testcontainers)

On Windows, use `mvnw.cmd` instead of `./mvnw` in the commands below. The project is a monorepo with `backend/` and `frontend/` as independent subdirectories — there is no root-level build system.

### 1. Start the database

```bash
docker compose up -d
```

This starts PostgreSQL 16 on `localhost:5432` with database `turnero`, user `turnero_user`, and password `turnero_pass`. The volume `postgres_data` persists data across restarts.

### 2. Start the backend

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# On Windows (PowerShell), quote the -D argument:
mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

The `dev` profile:
- Seeds real clinic data: 6 services, 5 professionals with availability windows, 2 patients, and 3 recurring blocks (EMSELLA reservations on Monday/Thursday and RPG sessions on Saturday)
- Promotes Marcela Altamirano to ADMIN role
- Uses an insecure JWT secret (dev only — never use in production)

Flyway runs the migrations automatically on startup. The backend starts on `http://localhost:8080`.

### 3. Start the frontend

```bash
cd frontend
npm install
ng serve
```

The frontend starts on `http://localhost:4200`. API requests go directly to `http://localhost:8080` (configured in the Angular environment files).

### 4. Log in

Once the dev profile is active, you can log in with any seeded professional account:

| Name | Email | Password | Role |
|------|-------|----------|------|
| Marcela Altamirano | `marcela.altamirano@equi.dev` | `changeme123` | PROFESSIONAL (+ ADMIN in dev) |
| Alejandra Gonzalez | `alejandra.gonzalez@equi.dev` | `changeme123` | PROFESSIONAL |
| Franco Lastra | `franco.lastra@equi.dev` | `changeme123` | PROFESSIONAL |
| Delia Furlan | `delia.furlan@equi.dev` | `changeme123` | PROFESSIONAL |
| Carolina Seona | `carolina.seona@equi.dev` | `changeme123` | PROFESSIONAL |

Patient accounts are also seeded (`juan.perez@example.com` / `changeme123`), or you can register a new patient through the UI.

### Seeded services

The dev profile seeds these clinic services:

| Service | Duration |
|---------|----------|
| Deporte y Traumatologia | 60 min |
| Reeducacion Postural (RPG) | 60 min |
| Drenaje Linfatico y Reflexologia | 60 min |
| Rehabilitacion Piso Pelvico | 60 min |
| EMSELLA | 30 min |
| Alquiler de Magnetoterapia | 60 min |

Each professional is associated with one or more services, and their availability windows define when they can be booked. Recurring blocks reserve the EMSELLA box on Monday/Thursday and Alejandra's RPG session on Saturday.

---

## Environment Variables

| Variable | Required | Default (dev) | Description |
|----------|----------|---------------|-------------|
| `ADMIN_EMAIL` | Yes (prod) | `marcela.altamirano@equi.dev` (dev profile only) | Email for the bootstrapped admin account |
| `ADMIN_PASSWORD` | Yes (prod) | Uses seeded user's password (dev profile only) | Password for the bootstrapped admin account |
| `JWT_SECRET` | No | `dev-only-insecure-secret-do-not-use-in-production-min-32-bytes` | Secret key for JWT signing (minimum 32 bytes) |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:4200` | Comma-separated allowed origins for CORS |

Outside the `dev` profile, the application **refuses to start** if `ADMIN_EMAIL` or `ADMIN_PASSWORD` are not set. This is a deliberate security decision — it prevents deploying to production with a missing or default admin account.

---

## Running the Tests

```bash
cd backend
./mvnw test
```

Tests require **Docker to be running** — each test class spins up its own PostgreSQL 16 container via Testcontainers. No manual database setup is needed; the containers are created and destroyed automatically. The Maven surefire plugin sets the `ADMIN_EMAIL` and `ADMIN_PASSWORD` environment variables so that `AdminBootstrapRunner` does not fail during the test context load.

The test suite covers:
- Authentication (registration, login, JWT validation, tampered/expired tokens, admin bootstrap, password changes)
- Appointment booking (happy path, validation errors, capacity conflicts, authorization)
- Status transitions (all valid and invalid transitions, role restrictions, cancellation time windows)
- IDOR protection (cross-patient and cross-professional access attempts)
- Recurring block capacity (EMSELLA box reservation, general capacity reduction, inactive blocks)
- Available slots, clinic agenda, month summary
- Service catalog

---

## Project Layout

```
turnero-kinesiologia/
├── docker-compose.yml          # PostgreSQL 16 for local development
├── backend/
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd
│   └── src/
│       ├── main/java/com/palavecino/backend/
│       │   ├── appointment/    # Core domain: booking, slots, capacity, state machine
│       │   ├── auth/           # Registration, login, JWT, password change
│       │   ├── availability/   # Professional availability windows
│       │   ├── config/         # Admin bootstrap, dev data seeder
│       │   ├── exception/      # Global exception handling, error responses
│       │   ├── patient/        # Patient entity and repository
│       │   ├── professional/   # Professional entity and API
│       │   ├── recurringblock/ # Recurring capacity blocks (EMSELLA, RPG)
│       │   ├── security/       # JWT filter, SecurityConfig, CORS, password encoder
│       │   ├── service/        # Clinic services (entity, API, catalog)
│       │   └── user/           # User account, roles
│       ├── main/resources/
│       │   ├── application.yaml
│       │   └── db/migration/   # Flyway migrations (V1–V3)
│       └── test/               # 111+ integration tests (Testcontainers + MockMvc)
├── frontend/
│   ├── angular.json
│   ├── package.json
│   └── src/app/
│       ├── core/               # Guards, interceptors, services, models
│       ├── pages/              # Login, register, book, my-appointments, agenda, home
│       └── shared/             # Reusable components (header, day-agenda)
└── docs/                       # Documentation (placeholder)
```

### Database schema

The schema is managed by Flyway across three migrations:

- **V1**: Core tables — `user_account`, `patient`, `professional`, `service`, `professional_service` (many-to-many), `availability`, `appointment` with status constraints and indexes
- **V2**: `recurring_block` table for weekly time blocks that permanently reserve a box (EMSELLA, RPG sessions)
- **V3**: `notifications_enabled` on patient, `must_change_password` on user account (for admin bootstrap flow)

All timestamps are stored as `TIMESTAMP WITHOUT TIME ZONE` with the JVM and Hibernate timezone pinned to `America/Argentina/Buenos_Aires`.

---

## Known Technical Debt

These are identified, reasoned trade-offs — not oversights. Each was evaluated against the clinic's expected volume and deferred with a clear path to resolution.

### TOCTOU race condition in booking

**What:** Between the time `findAvailableSlots` checks capacity and `bookAppointment` inserts the row, another request could book the same slot. The `@Transactional` annotation with `READ_COMMITTED` isolation provides basic protection, but under high concurrency, two requests could both see capacity as available and both succeed — violating the maximum-concurrent-appointments constraint.

**Why it's acceptable now:** The clinic has a handful of professionals and a low booking volume. The window for this race is milliseconds, and the probability of two patients booking the exact same slot simultaneously is negligible at this scale. The consequence of a violation (three concurrent appointments instead of two) is an operational inconvenience, not a data integrity failure.

**The real fix:** A database-level unique constraint on the relevant columns, or a pessimistic lock (`SELECT ... FOR UPDATE`) on the capacity check. Either would close the race condition completely. This is the first thing to address if booking volume grows.

### Service duration not frozen at booking time

**What:** The overlap calculation in `findAvailableSlots` and `checkCapacity` uses `service.getDurationMinutes()` — the current duration of the service, not the duration at the time the appointment was booked. If a service's duration is changed (e.g., from 60 to 45 minutes), the overlap calculation for existing appointments would shift.

**Why it's acceptable now:** Service durations are stable operational parameters. The clinic does not change them frequently, and when they do, the impact on past appointments is a known, low-probability edge case. Freezing duration at booking time would add a column to the appointment table and complicate every capacity query.

**The real fix:** Store `durationMinutes` on the `appointment` row at booking time, and use that value for overlap calculations on existing appointments. New bookings and slot generation would continue using the service's current duration.

### Cancellation window hardcoded in the frontend

**What:** The 24-hour minimum cancellation window is enforced server-side (via `clinic.min-cancellation-hours` in `application.yaml`), but the frontend also has its own hardcoded constant (`CANCELLATION_WINDOW_MS = 24 * 60 * 60 * 1000`) that disables the cancel button in the patient's appointment list. If the backend's `min-cancellation-hours` value changes, the frontend's UX would be out of sync until redeployed.

**Why it's acceptable now:** The 24-hour window is a policy decision that is unlikely to change. The backend enforces the real constraint; the frontend constant is purely a UX convenience to prevent showing a button that would be rejected with a 409 response.

**The real fix:** Expose the cancellation window via a configuration endpoint (or include it in the `/auth/me` response), and have the frontend read it from the server.

### Frontend re-fetches entire month summary after each action

**What:** When a professional confirms, completes, or cancels an appointment from the agenda view, the `AgendaComponent` re-fetches the entire month summary to update the calendar dot indicators. This is a full month's worth of data fetched to reflect a single status change.

**Why it's acceptable now:** The month summary query is a single `GROUP BY` over a bounded date range — it returns at most 31 rows. The response is small, the query is indexed, and the action frequency is low. The real cost is the network round-trip, not the query itself.

**The real fix:** Optimistically update the local calendar state (decrement/increment the day's count) without re-fetching, or cache the summary and invalidate only the affected day.

### No optimistic UI for slot selection

**What:** When a patient selects a time slot and confirms the booking, the frontend sends the request and waits for the server response. If another patient booked the same slot in the interim, the patient sees a 409 conflict and must re-select. There is no optimistic locking or soft reservation on the client side.

**Why it's acceptable now:** The booking flow is fast (a few seconds from slot selection to confirmation), and the clinic's volume makes simultaneous slot contention rare. The current UX — a clear error message and automatic slot re-fetch — is acceptable.

**The real fix:** A short-lived soft reservation (e.g., a Redis TTL lock on the slot for 30 seconds) would prevent contention, but adds infrastructure complexity. This would be worthwhile if the clinic expands to online-scheduled slots with high demand.

---

## Roadmap

### Near-term
- **CI with GitHub Actions** — automated test pipeline using Testcontainers (Docker-in-Docker or service containers)
- **Admin CRUD panel** — manage services, professionals, availability windows, and recurring blocks through the UI instead of database seeds
- **Refresh tokens** — extend session lifetime without requiring re-login, improving the experience for professionals who keep the agenda open all day

### Medium-term
- **Email notifications** — appointment confirmations, reminders, and cancellations sent via email. Email was chosen over WhatsApp for the MVP after evaluating the WhatsApp Business API: at the clinic's volume (~300 messages/month), the cost would be approximately $7–10 USD/month, but requires Meta business verification and a BSP (Business Solution Provider) integration. Email is simpler to implement and sufficient for now; WhatsApp remains a documented future option when the clinic begins operating and the volume justifies the integration cost.
- **Admin booking on behalf of patients** — a separate endpoint (distinct from patient self-service) that accepts `patientId` and is restricted to the ADMIN role, for phone or walk-in bookings where the receptionist books for the patient.

### Longer-term
- **WebSocket-based real-time agenda updates** — so professionals see new bookings and status changes without refreshing
- **Reporting and analytics** — appointment completion rates, no-show tracking, professional utilization dashboards
- **Multi-language support** — the UI is currently in Spanish (matching the clinic's locale); English support would be needed for the portfolio's international audience

---

## Author

**Franco Palavecino**
Junior developer, studying Tecnicatura Universitaria en Programación at UTN Córdoba, Argentina.

This project is my portfolio centerpiece — built to solve a real problem for a real client, and designed to demonstrate that I can reason about architecture, security, and trade-offs, not just write code.

GitHub: [github.com/francopalavecino2002](https://github.com/francopalavecino2002)

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
