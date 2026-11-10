-- OPTIONAL: fictional products and stock for local-only testing.
-- Do not run on shared/production Supabase without agreement.
-- This file is intentionally NOT part of automatic Docker initialization.
BEGIN;
INSERT INTO product (sku, name, current_price, low_stock_threshold, listing_status)
VALUES
  ('PA-100', 'Sample Product A', 80.00, 3, 'ACTIVE'),
  ('PB-200', 'Sample Product B', 45.00, 2, 'ACTIVE')
ON CONFLICT (sku) DO NOTHING;

INSERT INTO inventory (product_id, physical_stock, reserved_stock)
SELECT product_id,
       CASE sku WHEN 'PA-100' THEN 12 ELSE 5 END,
       0
FROM product
WHERE sku IN ('PA-100', 'PB-200')
ON CONFLICT (product_id) DO NOTHING;
COMMIT;
