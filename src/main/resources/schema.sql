-- ============================================================
--  PixelForge Restaurant Billing System - Database Schema
-- ============================================================

CREATE DATABASE IF NOT EXISTS pixelforge_restaurant;
USE pixelforge_restaurant;

-- ------------------------------------------------------------
-- Table: menu_items
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS menu_items (
    item_id    INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100)   NOT NULL,
    category   VARCHAR(50),
    price      DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ------------------------------------------------------------
-- Table: restaurant_tables
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS restaurant_tables (
    table_id      INT PRIMARY KEY,
    capacity      INT NOT NULL,
    status        ENUM('AVAILABLE', 'OCCUPIED') DEFAULT 'AVAILABLE',
    occupied_since TIMESTAMP NULL
);

-- ------------------------------------------------------------
-- Table: bills
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bills (
    bill_id      INT AUTO_INCREMENT PRIMARY KEY,
    table_id     INT,
    bill_details TEXT,
    subtotal     DECIMAL(10,2),
    gst          DECIMAL(10,2),
    grand_total  DECIMAL(10,2),
    bill_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (table_id) REFERENCES restaurant_tables(table_id)
);

-- ------------------------------------------------------------
-- Seed Data: Restaurant Tables (6 tables)
-- ------------------------------------------------------------
INSERT INTO restaurant_tables (table_id, capacity, status, occupied_since) VALUES
(1, 2, 'AVAILABLE', NULL),
(2, 2, 'AVAILABLE', NULL),
(3, 4, 'AVAILABLE', NULL),
(4, 4, 'AVAILABLE', NULL),
(5, 6, 'AVAILABLE', NULL),
(6, 8, 'AVAILABLE', NULL);

-- ------------------------------------------------------------
-- Seed Data: Menu Items (10 Indian restaurant items)
-- ------------------------------------------------------------
INSERT INTO menu_items (name, category, price) VALUES
-- Starters
('Paneer Tikka',        'Starters',     280.00),
('Chicken Seekh Kebab', 'Starters',     320.00),

-- Main Course
('Butter Chicken',      'Main Course',  360.00),
('Dal Makhani',         'Main Course',  280.00),
('Paneer Butter Masala','Main Course',  300.00),

-- Breads
('Butter Naan',         'Breads',        60.00),

-- Rice
('Veg Biryani',         'Rice',         250.00),
('Chicken Biryani',     'Rice',         320.00),

-- Beverages
('Sweet Lassi',         'Beverages',     80.00),

-- Desserts
('Gulab Jamun',         'Desserts',      90.00);
