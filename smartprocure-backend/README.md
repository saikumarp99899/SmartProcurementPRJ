# SmartProcure AI

An AI-powered Procurement and Supplier Management Platform built with Spring Boot 3 and React 19.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.2, Spring Security, JPA/Hibernate |
| Frontend | React 19, Vite, Tailwind CSS, TanStack React Query |
| Database | MySQL 8 |
| Auth | JWT + BCrypt |
| API Docs | Swagger/OpenAPI |
| Containerization | Docker + Docker Compose |

## Features

- **Role-Based Access Control** — Admin, Buyer, Approver roles with route-level and method-level security
- **Requisition Management** — Create, submit, approve/reject requisitions with full lifecycle tracking
- **Purchase Order Management** — Generate POs from approved requisitions, track status
- **Approval Workflow** — Approvers can review, approve, or reject procurement requests
- **Vendor Management** — Register and manage suppliers with status tracking
- **User Management** — Admin can create users with any role, activate/deactivate accounts
- **Audit Logging** — All actions tracked for compliance and accountability
- **Role-Specific Dashboards** — Tailored metrics for Admin, Buyer, and Approver

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────┐
│   React SPA     │────▶│  Spring Boot API  │────▶│  MySQL  │
│   (Vite/Nginx)  │     │  (REST + JWT)     │     │         │
└─────────────────┘     └──────────────────┘     └─────────┘
       :80                     :8080                 :3306
```

## Project Structure

```
Procure/
├── src/main/java/com/smartprocure/
│   ├── controller/       # REST API endpoints
│   ├── service/          # Business logic
│   ├── repository/       # Data access (Spring Data JPA)
│   ├── entity/           # JPA entities
│   ├── dto/              # Request/Response DTOs
│   ├── mapper/           # Entity ↔ DTO mappers
│   ├── security/         # JWT filter, UserDetails
│   ├── config/           # Security, Swagger config
│   ├── exception/        # Global exception handling
│   └── audit/            # Audit logging service
├── frontend/
│   ├── src/
│   │   ├── api/          # Axios API layer
│   │   ├── components/   # Shared UI components
│   │   ├── context/      # Auth context (React Context)
│   │   └── pages/        # Route-based pages
│   └── vite.config.js
├── Dockerfile            # Backend container
├── docker-compose.yml    # Full stack orchestration
└── pom.xml
```

## Getting Started

### Prerequisites

- Java 21
- Node.js 18+
- MySQL 8
- Docker (optional)

### Option 1: Run with Docker (Recommended)

```bash
docker-compose up --build
```

Access the app at http://localhost

### Option 2: Run Locally

**Backend:**
```bash
# Configure MySQL connection in src/main/resources/application.properties
./mvnw spring-boot:run
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```

Access the app at http://localhost:5173

## API Documentation

Once the backend is running, access Swagger UI:
- http://localhost:8080/swagger-ui.html

## Default Roles

| Role | Capabilities |
|------|-------------|
| ADMIN | Manage users, vendors, view all data, audit logs |
| BUYER | Create requisitions, manage POs, view vendors |
| APPROVER | Review and approve/reject requisitions |

## Status Flow

**Requisition:**
```
DRAFT → SUBMITTED → APPROVED/REJECTED → PO_CREATED → COMPLETED
```

**Purchase Order:**
```
DRAFT → SENT → ACCEPTED → DELIVERED → CLOSED
```

## Environment Variables (Docker)

| Variable | Default | Description |
|----------|---------|-------------|
| SPRING_DATASOURCE_URL | jdbc:mysql://mysql:3306/smartprocuredb | DB connection |
| SPRING_DATASOURCE_USERNAME | root | DB username |
| SPRING_DATASOURCE_PASSWORD | root123 | DB password |

## Roadmap

- [ ] Multi-level approval workflow
- [ ] RFQ (Request for Quotation) management
- [ ] Goods receipt module
- [ ] Invoice management
- [ ] AI vendor recommendation engine
- [ ] AI spend analysis
- [ ] Contract management
- [ ] Email notifications
- [ ] Kafka event-driven messaging
- [ ] Redis caching
