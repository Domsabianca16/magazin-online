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

## Deploying the frontend and API

The repository includes a root `Dockerfile` for the Java API. Render's Docker build uses the repository root as its context; the frontend remains an independent Vite project under `frontend`.

### Render

1. Create a Render PostgreSQL database. Choose Frankfurt to keep it near the API. For a real shop, choose a paid database plan: Render's Free Postgres is limited to 1 GB, has no backups, and expires after 30 days.
2. In Render, create a Web Service and connect `Domsabianca16/magazin-online`. Select Docker, Dockerfile path `./Dockerfile`, and Docker context `.`. Set health check path to `/api/health`.
3. In the database's Connect page, copy the **internal** host, port, database name, username, and password. In the API service's Environment page, set:
   - `DATABASE_URL=jdbc:postgresql://<internal-host>:<port>/<database-name>`
   - `DB_USERNAME=<database-username>`
   - `DB_PASSWORD=<database-password>`
   - `PORT=10000`
   - `DDL_AUTO=update`
   - `SHOW_SQL=false`
   - `ADMIN_USERNAME=<new-admin-name>`
   - `ADMIN_PASSWORD=<long-unique-password>`
   - `CORS_ALLOWED_ORIGINS=http://localhost:5173` initially; replace this with the Vercel production URL after the frontend deploys.
4. Deploy and check `https://<render-service>.onrender.com/api/health`. The API uses the JDBC URL form for `DATABASE_URL`; do not paste the raw `postgresql://...` URL into that variable.

### Vercel

1. Import the same GitHub repository as a new Vercel project.
2. Set Root Directory to `frontend`. Leave the Vite framework preset, build command `npm run build`, and output directory `dist` (Vercel detects these automatically).
3. Add `VITE_API_URL=https://<render-service>.onrender.com/api` for Production, then deploy.
4. Copy the deployed Vercel production URL into Render's `CORS_ALLOWED_ORIGINS`, save, and redeploy the API. The browser can then call the API across domains.

The administration page is available directly at `/admin` and is intentionally not linked from the public storefront. A fresh production database needs products added from that page. Hibernate `DDL_AUTO=update` is still in use; configure versioned migrations before relying on this deployment for production orders. Vercel Preview URLs are not automatically allowed by the API's exact-origin CORS list; add any preview origin you intend to use.
