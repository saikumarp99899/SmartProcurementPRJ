-- =============================================================
-- SmartProcure AI - Complete Database Schema
-- Database: smartprocuredb
-- =============================================================

CREATE DATABASE IF NOT EXISTS smartprocuredb;
USE smartprocuredb;

-- =============================================================
-- 1. departments
-- =============================================================
CREATE TABLE IF NOT EXISTS departments (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(150) NOT NULL UNIQUE,
    code     VARCHAR(50)  NOT NULL UNIQUE
);

-- =============================================================
-- 2. cost_centers
-- =============================================================
CREATE TABLE IF NOT EXISTS cost_centers (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    code          VARCHAR(50)  NOT NULL UNIQUE,
    department_id BIGINT       NOT NULL,
    CONSTRAINT fk_cc_department FOREIGN KEY (department_id) REFERENCES departments(id)
);

-- =============================================================
-- 3. roles
-- =============================================================
CREATE TABLE IF NOT EXISTS roles (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name ENUM('ADMIN','BUYER','APPROVER','VENDOR') NOT NULL UNIQUE
);

-- =============================================================
-- 4. users
-- =============================================================
CREATE TABLE IF NOT EXISTS users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    email      VARCHAR(150) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    status     ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    manager_id BIGINT,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_manager FOREIGN KEY (manager_id) REFERENCES users(id)
);

-- =============================================================
-- 5. user_roles (join table for User <-> Role many-to-many)
-- =============================================================
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- =============================================================
-- 6. vendors
-- =============================================================
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

-- =============================================================
-- 7. requisitions
-- =============================================================
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

-- =============================================================
-- 8. requisition_items
-- =============================================================
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

-- =============================================================
-- 9. approval_workflows
-- =============================================================
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

-- =============================================================
-- 10. approval_workflow_steps
-- =============================================================
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

-- =============================================================
-- 11. approvals (one row per step in a requisition's approval chain)
-- =============================================================
CREATE TABLE IF NOT EXISTS approvals (
    id                   BIGINT       AUTO_INCREMENT PRIMARY KEY,
    requisition_id       BIGINT       NOT NULL,
    approver_id          BIGINT,
    assigned_approver_id BIGINT,
    assigned_role        ENUM('ADMIN','BUYER','APPROVER','VENDOR'),
    approval_level       INT          NOT NULL DEFAULT 1,
    status               ENUM('WAITING','PENDING','APPROVED','REJECTED','SKIPPED') NOT NULL DEFAULT 'PENDING',
    rule_name            VARCHAR(150),
    workflow_name        VARCHAR(150),
    comments             VARCHAR(1000),
    approved_at          DATETIME,
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appr_requisition  FOREIGN KEY (requisition_id)       REFERENCES requisitions(id),
    CONSTRAINT fk_appr_approver     FOREIGN KEY (approver_id)          REFERENCES users(id),
    CONSTRAINT fk_appr_assigned     FOREIGN KEY (assigned_approver_id) REFERENCES users(id)
);

-- =============================================================
-- 12. approval_rules (LEGACY — migrated to workflows on startup)
-- =============================================================
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

-- =============================================================
-- 13. purchase_orders
-- =============================================================
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

-- =============================================================
-- 14. purchase_order_items
-- =============================================================
CREATE TABLE IF NOT EXISTS purchase_order_items (
    id                BIGINT         AUTO_INCREMENT PRIMARY KEY,
    purchase_order_id BIGINT         NOT NULL,
    item_name         VARCHAR(200)   NOT NULL,
    quantity          INT            NOT NULL,
    unit_price        DECIMAL(15, 2) NOT NULL,
    total_price       DECIMAL(15, 2) NOT NULL,
    CONSTRAINT fk_poi_purchase_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id)
);

-- =============================================================
-- 15. audit_logs
-- =============================================================
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

-- =============================================================
-- 16. proxy_sessions
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
-- 17. company_addresses (ship-to, bill-to locations)
-- =============================================================
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

-- Add new columns to purchase_orders table
ALTER TABLE purchase_orders ADD COLUMN ship_to_address_id BIGINT;
ALTER TABLE purchase_orders ADD COLUMN bill_to_address_id BIGINT;
ALTER TABLE purchase_orders ADD COLUMN payment_terms VARCHAR(100) DEFAULT '30 Days Net';
ALTER TABLE purchase_orders ADD COLUMN shipping_method VARCHAR(100);
ALTER TABLE purchase_orders ADD COLUMN notes TEXT;
ALTER TABLE purchase_orders ADD COLUMN subtotal DECIMAL(15,2);
ALTER TABLE purchase_orders ADD COLUMN tax_amount DECIMAL(15,2) DEFAULT 0.00;
ALTER TABLE purchase_orders ADD COLUMN shipping_cost DECIMAL(15,2) DEFAULT 0.00;

-- =============================================================
-- Seed Data
-- =============================================================

-- Roles
INSERT IGNORE INTO roles (name) VALUES ('ADMIN'), ('BUYER'), ('APPROVER'), ('VENDOR');

-- Departments
INSERT IGNORE INTO departments (name, code) VALUES
    ('Information Technology', 'IT'),
    ('Human Resources',        'HR'),
    ('Finance',                'FIN'),
    ('Operations',             'OPS'),
    ('Marketing',              'MKT'),
    ('Procurement',            'PROC');

-- Cost Centers
INSERT IGNORE INTO cost_centers (name, code, department_id) VALUES
    ('IT Infrastructure',     'CC-IT-001',   (SELECT id FROM departments WHERE code = 'IT')),
    ('IT Software',           'CC-IT-002',   (SELECT id FROM departments WHERE code = 'IT')),
    ('Finance Operations',    'CC-FIN-001',  (SELECT id FROM departments WHERE code = 'FIN')),
    ('HR Operations',         'CC-HR-001',   (SELECT id FROM departments WHERE code = 'HR')),
    ('Operations General',    'CC-OPS-001',  (SELECT id FROM departments WHERE code = 'OPS')),
    ('Marketing Campaigns',   'CC-MKT-001',  (SELECT id FROM departments WHERE code = 'MKT')),
    ('Procurement General',   'CC-PROC-001', (SELECT id FROM departments WHERE code = 'PROC'));

-- Admin user (password: Admin@123 — BCrypt hash)
INSERT IGNORE INTO users (first_name, last_name, email, password, status) VALUES
    ('System', 'Admin', 'admin@smartprocure.com',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ACTIVE');

-- Assign ADMIN role to admin user
INSERT IGNORE INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r
    WHERE u.email = 'admin@smartprocure.com' AND r.name = 'ADMIN';

-- Default company addresses
INSERT IGNORE INTO company_addresses (address_type, company_name, address_line1, city, state, postal_code, country, phone, is_default)
VALUES 
('SHIP_TO', 'SmartProcure HQ', '123 Technology Park, Whitefield', 'Bangalore', 'Karnataka', '560066', 'India', '+91-80-4567-8900', TRUE),
('BILL_TO', 'SmartProcure Pvt Ltd', '123 Technology Park, Whitefield', 'Bangalore', 'Karnataka', '560066', 'India', '+91-80-4567-8900', TRUE);
