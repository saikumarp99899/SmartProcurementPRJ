-- =============================================================
-- SmartProcure AI - Complete Database Migration History
-- Run these queries in order on a fresh MySQL instance
-- =============================================================

CREATE DATABASE IF NOT EXISTS smartprocuredb;
USE smartprocuredb;

-- =============================================================
-- V1.0 — Initial Schema (2026-08-12)
-- Base tables: users, roles, vendors, requisitions, POs, audit
-- =============================================================

-- Departments
CREATE TABLE IF NOT EXISTS departments (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(150) NOT NULL UNIQUE,
    code     VARCHAR(50)  NOT NULL UNIQUE
);

-- Cost Centers
CREATE TABLE IF NOT EXISTS cost_centers (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    code          VARCHAR(50)  NOT NULL UNIQUE,
    department_id BIGINT       NOT NULL,
    CONSTRAINT fk_cc_department FOREIGN KEY (department_id) REFERENCES departments(id)
);

-- Roles
CREATE TABLE IF NOT EXISTS roles (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name ENUM('ADMIN','BUYER','APPROVER','VENDOR') NOT NULL UNIQUE
);

-- Users
CREATE TABLE IF NOT EXISTS users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    email      VARCHAR(150) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    status     ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     ON UPDATE CURRENT_TIMESTAMP
);

-- User-Role mapping
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- Vendors
CREATE TABLE IF NOT EXISTS vendors (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    vendor_code VARCHAR(50)    NOT NULL UNIQUE,
    vendor_name VARCHAR(200)   NOT NULL,
    email       VARCHAR(150)   NOT NULL,
    phone       VARCHAR(30),
    address     VARCHAR(500),
    city        VARCHAR(100),
    state       VARCHAR(100),
    country     VARCHAR(100),
    status      ENUM('ACTIVE','INACTIVE','SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    rating      DECIMAL(3, 2),
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME       ON UPDATE CURRENT_TIMESTAMP
);

-- Requisitions
CREATE TABLE IF NOT EXISTS requisitions (
    id                  BIGINT         AUTO_INCREMENT PRIMARY KEY,
    requisition_number  VARCHAR(50)    NOT NULL UNIQUE,
    requester_id        BIGINT         NOT NULL,
    department_id       BIGINT,
    cost_center_id      BIGINT,
    description         VARCHAR(1000),
    total_amount        DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    status              ENUM('DRAFT','SUBMITTED','APPROVED','REJECTED','PO_CREATED','COMPLETED') NOT NULL DEFAULT 'DRAFT',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME       ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_req_requester   FOREIGN KEY (requester_id)   REFERENCES users(id),
    CONSTRAINT fk_req_department  FOREIGN KEY (department_id)  REFERENCES departments(id),
    CONSTRAINT fk_req_cost_center FOREIGN KEY (cost_center_id) REFERENCES cost_centers(id)
);

-- Requisition Items
CREATE TABLE IF NOT EXISTS requisition_items (
    id              BIGINT         AUTO_INCREMENT PRIMARY KEY,
    requisition_id  BIGINT         NOT NULL,
    item_name       VARCHAR(200)   NOT NULL,
    description     VARCHAR(500),
    quantity        INT            NOT NULL,
    unit_price      DECIMAL(15, 2) NOT NULL,
    total_price     DECIMAL(15, 2) NOT NULL,
    CONSTRAINT fk_ri_requisition FOREIGN KEY (requisition_id) REFERENCES requisitions(id)
);

-- Approvals (basic single-level)
CREATE TABLE IF NOT EXISTS approvals (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    requisition_id  BIGINT       NOT NULL,
    approver_id     BIGINT,
    approval_level  INT          NOT NULL DEFAULT 1,
    status          ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    comments        VARCHAR(1000),
    approved_at     DATETIME,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appr_requisition FOREIGN KEY (requisition_id) REFERENCES requisitions(id),
    CONSTRAINT fk_appr_approver    FOREIGN KEY (approver_id)    REFERENCES users(id)
);

-- Purchase Orders
CREATE TABLE IF NOT EXISTS purchase_orders (
    id                     BIGINT         AUTO_INCREMENT PRIMARY KEY,
    po_number              VARCHAR(50)    NOT NULL UNIQUE,
    requisition_id         BIGINT         NOT NULL UNIQUE,
    vendor_id              BIGINT         NOT NULL,
    created_by_id          BIGINT         NOT NULL,
    order_date             DATE           NOT NULL,
    expected_delivery_date DATE,
    total_amount           DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    status                 ENUM('DRAFT','SENT','ACCEPTED','DELIVERED','CLOSED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    created_at             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME       ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_po_requisition FOREIGN KEY (requisition_id) REFERENCES requisitions(id),
    CONSTRAINT fk_po_vendor      FOREIGN KEY (vendor_id)      REFERENCES vendors(id),
    CONSTRAINT fk_po_created_by  FOREIGN KEY (created_by_id)  REFERENCES users(id)
);

-- Purchase Order Items
CREATE TABLE IF NOT EXISTS purchase_order_items (
    id                BIGINT         AUTO_INCREMENT PRIMARY KEY,
    purchase_order_id BIGINT         NOT NULL,
    item_name         VARCHAR(200)   NOT NULL,
    quantity          INT            NOT NULL,
    unit_price        DECIMAL(15, 2) NOT NULL,
    total_price       DECIMAL(15, 2) NOT NULL,
    CONSTRAINT fk_poi_purchase_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id)
);

-- Audit Logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT,
    action      ENUM('LOGIN','USER_CREATED','USER_UPDATED','USER_ACTIVATED',
                     'USER_DEACTIVATED','VENDOR_CREATED','VENDOR_UPDATED',
                     'VENDOR_DEACTIVATED','REQUISITION_CREATED','REQUISITION_SUBMITTED',
                     'REQUISITION_APPROVED','REQUISITION_REJECTED',
                     'PO_CREATED','PO_UPDATED','PO_CANCELLED',
                     'PROXY_LOGIN_START','PROXY_LOGIN_END','PROXY_LOGIN_EXPIRED',
                     'PROXY_LOGIN_DENIED') NOT NULL,
    entity_type VARCHAR(50),
    entity_id   BIGINT,
    description VARCHAR(500),
    timestamp   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_al_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- V1.0 Seed Data
INSERT IGNORE INTO roles (name) VALUES ('ADMIN'), ('BUYER'), ('APPROVER'), ('VENDOR');

INSERT IGNORE INTO departments (name, code) VALUES
    ('Information Technology', 'IT'),
    ('Human Resources',        'HR'),
    ('Finance',                'FIN'),
    ('Operations',             'OPS'),
    ('Marketing',              'MKT'),
    ('Procurement',            'PROC');

INSERT IGNORE INTO cost_centers (name, code, department_id) VALUES
    ('IT Infrastructure',     'CC-IT-001',   (SELECT id FROM departments WHERE code = 'IT')),
    ('IT Software',           'CC-IT-002',   (SELECT id FROM departments WHERE code = 'IT')),
    ('Finance Operations',    'CC-FIN-001',  (SELECT id FROM departments WHERE code = 'FIN')),
    ('HR Operations',         'CC-HR-001',   (SELECT id FROM departments WHERE code = 'HR')),
    ('Operations General',    'CC-OPS-001',  (SELECT id FROM departments WHERE code = 'OPS')),
    ('Marketing Campaigns',   'CC-MKT-001',  (SELECT id FROM departments WHERE code = 'MKT')),
    ('Procurement General',   'CC-PROC-001', (SELECT id FROM departments WHERE code = 'PROC'));

-- Admin user (password: Admin@123)
INSERT IGNORE INTO users (first_name, last_name, email, password, status) VALUES
    ('System', 'Admin', 'admin@smartprocure.com',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ACTIVE');

INSERT IGNORE INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r
    WHERE u.email = 'admin@smartprocure.com' AND r.name = 'ADMIN';

-- Sample Buyer (password: Buyer@123)
INSERT IGNORE INTO users (first_name, last_name, email, password, status) VALUES
    ('John', 'Buyer', 'buyer@smartprocure.com',
     '$2a$10$TH.WxmpPetOr8xIDIVPv0.5fz.hHqRc6aXBSblN8.bPjF/gu7BIWC', 'ACTIVE');

INSERT IGNORE INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r
    WHERE u.email = 'buyer@smartprocure.com' AND r.name = 'BUYER';

-- Sample Approver (password: Approver@123)
INSERT IGNORE INTO users (first_name, last_name, email, password, status) VALUES
    ('Jane', 'Approver', 'approver@smartprocure.com',
     '$2a$10$pxPpB9GmgNlLNqOaRXFNQ.lbS0JlI6a6m.dQUOiU6CY/N0mDmHOT2', 'ACTIVE');

INSERT IGNORE INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r
    WHERE u.email = 'approver@smartprocure.com' AND r.name = 'APPROVER';

-- Sample Vendors
INSERT IGNORE INTO vendors (vendor_code, vendor_name, email, phone, city, state, country, status, rating) VALUES
    ('VND-001', 'TechSupply Corp',      'contact@techsupply.com',  '+1-555-0100', 'San Francisco', 'CA', 'USA', 'ACTIVE', 4.50),
    ('VND-002', 'Office Pro Solutions', 'info@officepro.com',      '+1-555-0200', 'New York',      'NY', 'USA', 'ACTIVE', 4.20),
    ('VND-003', 'Global IT Services',   'sales@globalit.com',      '+1-555-0300', 'Austin',        'TX', 'USA', 'ACTIVE', 3.80),
    ('VND-004', 'Premium Supplies Ltd', 'hello@premiumsupply.com', '+1-555-0400', 'Chicago',       'IL', 'USA', 'ACTIVE', 4.70),
    ('VND-005', 'FastShip Logistics',   'ops@fastship.com',        '+1-555-0500', 'Dallas',        'TX', 'USA', 'INACTIVE', 3.50);


-- =============================================================
-- V2.0 — Approval Workflow Engine (2026-08-15)
-- Multi-level approval with amount/department routing,
-- three approver types, reporting manager support
-- =============================================================

-- Add reporting manager to users
ALTER TABLE users
    ADD COLUMN manager_id BIGINT NULL AFTER status;

ALTER TABLE users
    ADD CONSTRAINT fk_user_manager FOREIGN KEY (manager_id) REFERENCES users(id);

-- Expand approvals for workflow-based chains
ALTER TABLE approvals
    ADD COLUMN assigned_approver_id BIGINT NULL AFTER approver_id,
    ADD COLUMN assigned_role ENUM('ADMIN','BUYER','APPROVER','VENDOR') NULL AFTER assigned_approver_id,
    ADD COLUMN rule_name VARCHAR(150) NULL AFTER approval_level,
    ADD COLUMN workflow_name VARCHAR(150) NULL AFTER rule_name;

ALTER TABLE approvals
    ADD CONSTRAINT fk_appr_assigned FOREIGN KEY (assigned_approver_id) REFERENCES users(id);

ALTER TABLE approvals
    MODIFY COLUMN status ENUM('WAITING','PENDING','APPROVED','REJECTED','SKIPPED') NOT NULL DEFAULT 'PENDING';

-- Approval Workflows
CREATE TABLE IF NOT EXISTS approval_workflows (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(150)   NOT NULL UNIQUE,
    description   VARCHAR(500),
    min_amount    DECIMAL(15, 2) NOT NULL,
    max_amount    DECIMAL(15, 2),
    department_id BIGINT,
    priority      INT            NOT NULL DEFAULT 0,
    active        BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME       ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_aw_department FOREIGN KEY (department_id) REFERENCES departments(id)
);

-- Approval Workflow Steps
CREATE TABLE IF NOT EXISTS approval_workflow_steps (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id   BIGINT       NOT NULL,
    step_order    INT          NOT NULL,
    name          VARCHAR(150) NOT NULL,
    approver_type ENUM('SPECIFIC_USER','ROLE','REQUESTER_MANAGER') NOT NULL,
    approver_id   BIGINT,
    approver_role ENUM('ADMIN','BUYER','APPROVER','VENDOR'),
    CONSTRAINT fk_aws_workflow FOREIGN KEY (workflow_id) REFERENCES approval_workflows(id) ON DELETE CASCADE,
    CONSTRAINT fk_aws_approver FOREIGN KEY (approver_id) REFERENCES users(id)
);

-- Legacy Approval Rules (migrated to workflows on app startup)
CREATE TABLE IF NOT EXISTS approval_rules (
    id             BIGINT         AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(150)   NOT NULL,
    approval_level INT            NOT NULL,
    min_amount     DECIMAL(15, 2) NOT NULL,
    max_amount     DECIMAL(15, 2),
    approver_id    BIGINT         NOT NULL,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME       ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ar_approver FOREIGN KEY (approver_id) REFERENCES users(id)
);

-- Set sample approver as buyer's manager (enables REQUESTER_MANAGER steps)
UPDATE users
SET manager_id = (SELECT id FROM (SELECT id FROM users WHERE email = 'approver@smartprocure.com') AS t)
WHERE email = 'buyer@smartprocure.com';

-- Sample Workflow: Standard Approval (any amount, single approver step)
INSERT IGNORE INTO approval_workflows (name, description, min_amount, max_amount, priority, active) VALUES
    ('Standard Approval', 'Default workflow — routes all requisitions to any approver', 0, NULL, 0, TRUE);

INSERT IGNORE INTO approval_workflow_steps (workflow_id, step_order, name, approver_type, approver_role) VALUES
    ((SELECT id FROM approval_workflows WHERE name = 'Standard Approval'), 1, 'Approver Review', 'ROLE', 'APPROVER');


-- =============================================================
-- V3.0 — Proxy Login / Admin Impersonation (2026-08-20)
-- Enables ADMIN users to impersonate other users with
-- time-limited sessions and full audit trail
-- =============================================================

CREATE TABLE IF NOT EXISTS proxy_sessions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id        BIGINT       NOT NULL,
    target_user_id  BIGINT       NOT NULL,
    token           VARCHAR(500) NOT NULL,
    started_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      DATETIME     NOT NULL,
    ended_at        DATETIME,
    status          ENUM('ACTIVE','EXPIRED','ENDED') NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_ps_admin  FOREIGN KEY (admin_id)       REFERENCES users(id),
    CONSTRAINT fk_ps_target FOREIGN KEY (target_user_id) REFERENCES users(id)
);

-- =============================================================
-- V4.0 — Enhanced Purchase Order (Company Addresses, PO Terms)
-- Adds ship-to/bill-to address support, payment terms,
-- shipping method, notes, and cost breakdown fields
-- =============================================================

-- Company Addresses (ship-to, bill-to locations)
CREATE TABLE IF NOT EXISTS company_addresses (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    address_type  ENUM('SHIP_TO','BILL_TO') NOT NULL,
    company_name  VARCHAR(200) NOT NULL,
    address_line1 VARCHAR(200) NOT NULL,
    address_line2 VARCHAR(200),
    city          VARCHAR(100) NOT NULL,
    state         VARCHAR(100) NOT NULL,
    postal_code   VARCHAR(20) NOT NULL,
    country       VARCHAR(100) NOT NULL DEFAULT 'India',
    phone         VARCHAR(30),
    is_default    BOOLEAN DEFAULT FALSE,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Extend purchase_orders with address references and PO terms
ALTER TABLE purchase_orders ADD COLUMN ship_to_address_id BIGINT;
ALTER TABLE purchase_orders ADD COLUMN bill_to_address_id BIGINT;
ALTER TABLE purchase_orders ADD COLUMN payment_terms VARCHAR(100) DEFAULT '30 Days Net';
ALTER TABLE purchase_orders ADD COLUMN shipping_method VARCHAR(100);
ALTER TABLE purchase_orders ADD COLUMN notes TEXT;
ALTER TABLE purchase_orders ADD COLUMN subtotal DECIMAL(15,2);
ALTER TABLE purchase_orders ADD COLUMN tax_amount DECIMAL(15,2) DEFAULT 0.00;
ALTER TABLE purchase_orders ADD COLUMN shipping_cost DECIMAL(15,2) DEFAULT 0.00;

-- Default company addresses
INSERT IGNORE INTO company_addresses (address_type, company_name, address_line1, city, state, postal_code, country, phone, is_default)
VALUES 
('SHIP_TO', 'SmartProcure HQ', '123 Technology Park, Whitefield', 'Bangalore', 'Karnataka', '560066', 'India', '+91-80-4567-8900', TRUE),
('BILL_TO', 'SmartProcure Pvt Ltd', '123 Technology Park, Whitefield', 'Bangalore', 'Karnataka', '560066', 'India', '+91-80-4567-8900', TRUE);

-- =============================================================
-- END OF MIGRATIONS
-- =============================================================
