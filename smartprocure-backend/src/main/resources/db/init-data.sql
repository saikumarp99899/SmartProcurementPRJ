-- ============================================================
-- SmartProcure AI - Database Initialization Script
-- Run these queries AFTER the application starts once
-- (Hibernate creates the tables with ddl-auto: update)
-- Database: smartprocuredb
-- ============================================================

-- Step 1: Create the database (run this first in MySQL Workbench)
CREATE DATABASE IF NOT EXISTS smartprocuredb;
USE smartprocuredb;

-- ============================================================
-- SEED ROLES (must exist before creating users)
-- ============================================================
INSERT INTO roles (name) VALUES ('ADMIN')     ON DUPLICATE KEY UPDATE name = name;
INSERT INTO roles (name) VALUES ('BUYER')     ON DUPLICATE KEY UPDATE name = name;
INSERT INTO roles (name) VALUES ('APPROVER')  ON DUPLICATE KEY UPDATE name = name;
INSERT INTO roles (name) VALUES ('VENDOR')    ON DUPLICATE KEY UPDATE name = name;

-- ============================================================
-- SEED DEPARTMENTS
-- ============================================================
INSERT INTO departments (name, code) VALUES ('Information Technology', 'IT')         ON DUPLICATE KEY UPDATE name = name;
INSERT INTO departments (name, code) VALUES ('Human Resources',        'HR')         ON DUPLICATE KEY UPDATE name = name;
INSERT INTO departments (name, code) VALUES ('Finance',                'FIN')        ON DUPLICATE KEY UPDATE name = name;
INSERT INTO departments (name, code) VALUES ('Operations',             'OPS')        ON DUPLICATE KEY UPDATE name = name;
INSERT INTO departments (name, code) VALUES ('Marketing',              'MKT')        ON DUPLICATE KEY UPDATE name = name;
INSERT INTO departments (name, code) VALUES ('Procurement',            'PROC')       ON DUPLICATE KEY UPDATE name = name;

-- ============================================================
-- SEED COST CENTERS (linked to departments)
-- ============================================================
-- IT cost centers
INSERT INTO cost_centers (name, code, department_id)
SELECT 'IT Infrastructure', 'CC-IT-001', id FROM departments WHERE code = 'IT'
ON DUPLICATE KEY UPDATE name = name;

INSERT INTO cost_centers (name, code, department_id)
SELECT 'IT Software', 'CC-IT-002', id FROM departments WHERE code = 'IT'
ON DUPLICATE KEY UPDATE name = name;

-- HR cost centers
INSERT INTO cost_centers (name, code, department_id)
SELECT 'HR Operations', 'CC-HR-001', id FROM departments WHERE code = 'HR'
ON DUPLICATE KEY UPDATE name = name;

-- Finance cost centers
INSERT INTO cost_centers (name, code, department_id)
SELECT 'Finance Operations', 'CC-FIN-001', id FROM departments WHERE code = 'FIN'
ON DUPLICATE KEY UPDATE name = name;

-- Operations cost centers
INSERT INTO cost_centers (name, code, department_id)
SELECT 'Operations General', 'CC-OPS-001', id FROM departments WHERE code = 'OPS'
ON DUPLICATE KEY UPDATE name = name;

-- Marketing cost centers
INSERT INTO cost_centers (name, code, department_id)
SELECT 'Marketing Campaigns', 'CC-MKT-001', id FROM departments WHERE code = 'MKT'
ON DUPLICATE KEY UPDATE name = name;

-- Procurement cost centers
INSERT INTO cost_centers (name, code, department_id)
SELECT 'Procurement General', 'CC-PROC-001', id FROM departments WHERE code = 'PROC'
ON DUPLICATE KEY UPDATE name = name;

-- ============================================================
-- SEED ADMIN USER
-- Password: Admin@123  (BCrypt hash)
-- You can change this password after first login via API
-- ============================================================
INSERT INTO users (first_name, last_name, email, password, status, created_at, updated_at)
VALUES (
    'System',
    'Admin',
    'admin@smartprocure.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'ACTIVE',
    NOW(),
    NOW()
) ON DUPLICATE KEY UPDATE email = email;

-- Assign ADMIN role to admin user
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.email = 'admin@smartprocure.com' AND r.name = 'ADMIN'
ON DUPLICATE KEY UPDATE user_id = user_id;

-- ============================================================
-- SEED SAMPLE BUYER USER
-- Password: Buyer@123  (BCrypt hash)
-- ============================================================
INSERT INTO users (first_name, last_name, email, password, status, created_at, updated_at)
VALUES (
    'John',
    'Buyer',
    'buyer@smartprocure.com',
    '$2a$10$TH.WxmpPetOr8xIDIVPv0.5fz.hHqRc6aXBSblN8.bPjF/gu7BIWC',
    'ACTIVE',
    NOW(),
    NOW()
) ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.email = 'buyer@smartprocure.com' AND r.name = 'BUYER'
ON DUPLICATE KEY UPDATE user_id = user_id;

-- ============================================================
-- SEED SAMPLE APPROVER USER
-- Password: Approver@123  (BCrypt hash)
-- ============================================================
INSERT INTO users (first_name, last_name, email, password, status, created_at, updated_at)
VALUES (
    'Jane',
    'Approver',
    'approver@smartprocure.com',
    '$2a$10$pxPpB9GmgNlLNqOaRXFNQ.lbS0JlI6a6m.dQUOiU6CY/N0mDmHOT2',
    'ACTIVE',
    NOW(),
    NOW()
) ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.email = 'approver@smartprocure.com' AND r.name = 'APPROVER'
ON DUPLICATE KEY UPDATE user_id = user_id;

-- ============================================================
-- SEED SAMPLE VENDORS
-- ============================================================
INSERT INTO vendors (vendor_code, vendor_name, email, phone, address, city, state, country, status, rating, created_at, updated_at)
VALUES
('VND-001', 'TechSupply Corp',       'contact@techsupply.com',   '+1-555-0100', '123 Tech Street',  'San Francisco', 'CA', 'USA', 'ACTIVE', 4.50, NOW(), NOW()),
('VND-002', 'Office Pro Solutions',  'info@officepro.com',       '+1-555-0200', '456 Business Ave', 'New York',      'NY', 'USA', 'ACTIVE', 4.20, NOW(), NOW()),
('VND-003', 'Global IT Services',    'sales@globalit.com',       '+1-555-0300', '789 IT Boulevard', 'Austin',        'TX', 'USA', 'ACTIVE', 3.80, NOW(), NOW()),
('VND-004', 'Premium Supplies Ltd',  'hello@premiumsupply.com',  '+1-555-0400', '321 Supply Road',  'Chicago',       'IL', 'USA', 'ACTIVE', 4.70, NOW(), NOW()),
('VND-005', 'FastShip Logistics',    'ops@fastship.com',         '+1-555-0500', '654 Logistics Way','Dallas',        'TX', 'USA', 'INACTIVE', 3.50, NOW(), NOW())
ON DUPLICATE KEY UPDATE vendor_code = vendor_code;

-- ============================================================
-- VERIFICATION QUERIES (run to confirm data was inserted)
-- ============================================================
SELECT 'Roles' AS table_name, COUNT(*) AS row_count FROM roles
UNION ALL
SELECT 'Departments', COUNT(*) FROM departments
UNION ALL
SELECT 'Cost Centers', COUNT(*) FROM cost_centers
UNION ALL
SELECT 'Users', COUNT(*) FROM users
UNION ALL
SELECT 'Vendors', COUNT(*) FROM vendors;

-- View users with their roles
SELECT u.id, u.first_name, u.last_name, u.email, u.status, r.name AS role
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON r.id = ur.role_id;
