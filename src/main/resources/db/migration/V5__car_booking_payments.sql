ALTER TABLE car_bookings
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    ADD COLUMN IF NOT EXISTS payment_currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    ADD COLUMN IF NOT EXISTS exchange_rate_source VARCHAR(64),
    ADD COLUMN IF NOT EXISTS payment_quote_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(128),
    ADD COLUMN IF NOT EXISTS payment_checkout_url VARCHAR(255);

ALTER TABLE car_bookings
    ALTER COLUMN exchange_rate TYPE NUMERIC(10, 6)
    USING exchange_rate::NUMERIC(10, 6);

CREATE UNIQUE INDEX IF NOT EXISTS uk_car_bookings_payment_reference
    ON car_bookings (payment_reference)
    WHERE payment_reference IS NOT NULL;
