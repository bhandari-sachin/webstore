
-- Webstore Database Features
-- Requires the original webstore schema.
-- Run using the MariaDB command-line client.

-- 1. Price index for product searches
CREATE INDEX IF NOT EXISTS idx_products_price
    ON products(price);

-- 2. Product catalog view
CREATE OR REPLACE VIEW product_catalog_view AS
SELECT
    p.id AS product_id,
    p.name AS product_name,
    p.price,
    p.stock_quantity,
    p.category_id,
    c.name AS category_name
FROM products p
         LEFT JOIN productcategories c
                   ON p.category_id = c.id;

-- 3. Product price history
CREATE TABLE IF NOT EXISTS product_price_history (
                                                     id INT AUTO_INCREMENT PRIMARY KEY,
                                                     product_id INT NOT NULL,
                                                     old_price DECIMAL(10,2) NOT NULL,
    new_price DECIMAL(10,2) NOT NULL,
    changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_price_history_product
    FOREIGN KEY (product_id)
    REFERENCES products(id)
    );

-- 4. Trigger to record price changes
DELIMITER //

CREATE OR REPLACE TRIGGER trg_product_price_history
AFTER UPDATE ON products
                           FOR EACH ROW
BEGIN
    IF OLD.price <> NEW.price THEN
        INSERT INTO product_price_history (
            product_id,
            old_price,
            new_price
        )
        VALUES (
            NEW.id,
            OLD.price,
            NEW.price
        );
END IF;
END//

DELIMITER ;
