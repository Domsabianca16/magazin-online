# Atelier Store

Atelier is a Vite + React storefront backed by a Spring Boot API and PostgreSQL. Orders can be placed with cash on delivery; card payments are not configured. Customer accounts, persisted customer carts, order history, payment integration, and several other commerce APIs are not implemented yet.

## Local development

Requirements: Node.js 20.19+ (or 22.12+), Java 21, and PostgreSQL 15+.

1. Create a PostgreSQL database named `ecommerce_db` and a local database user.
2. In `backend`, copy `.env.example` to `.env` and set `DB_PASSWORD` to your local PostgreSQL password. Set a unique `ADMIN_USERNAME` and strong `ADMIN_PASSWORD`. `.env` is ignored by Git; never commit actual credentials.
3. From the project root, start the API in PowerShell with `$env:SPRING_PROFILES_ACTIVE='dev'; cd backend; .\gradlew.bat bootRun`. The API listens on port 8081. `GET /api/health` is the health check. The `dev` profile seeds two demo products only when the catalogue is empty.
4. In a second terminal, run `cd frontend; npm install; npm run dev`. If the API URL differs from the default, copy `frontend/.env.example` to `frontend/.env.local` and edit it.

Hibernate currently creates/updates the existing product and user tables with `DDL_AUTO=update`. This is suitable only for local development. There are no versioned migrations in the current backend. Before production, add Flyway migrations and set `DDL_AUTO=validate`.

## Checks

From `frontend`, run `npm run build` and `npm run lint`. From `backend`, run `./gradlew test` (PowerShell: `./gradlew.bat test`).

## Current API and limitations

- `GET /api/products` returns the persisted product catalogue.
- `POST /api/products` requires HTTP Basic authentication as the configured administrator.
- Products are seeded in development only when the database is empty.
- `POST /api/orders` places a guest order using cash on delivery. It checks and locks inventory inside a database transaction, stores a price snapshot and delivery details, reduces stock, calculates shipping on the server, and uses the `Idempotency-Key` header to protect against duplicate submissions. The response status is `PENDING`; no card payment is claimed or processed. Restart the backend after pulling these changes so the new endpoint and order tables are loaded.
- Customer accounts, reset password, managed order history, category, review, coupon, and payment provider endpoints do not exist yet. Card payment is not offered.
- The administrator is configured from environment variables. Customer authentication and durable role management still need implementation before production.

Frontend API configuration uses `VITE_API_URL`. Backend CORS uses comma-separated `CORS_ALLOWED_ORIGINS`. Render can run the backend with `./gradlew bootRun`; configure `DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PORT`, and admin credentials in its environment. Vercel should set `VITE_API_URL` to the deployed API base URL.
