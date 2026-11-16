DO $$
BEGIN
    IF to_regclass('public.bookings') IS NOT NULL THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_bookings_status_created_at '
             || 'ON public.bookings (status, created_at)';
    END IF;

    IF to_regclass('public.car_bookings') IS NOT NULL THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_car_bookings_status_created_at '
             || 'ON public.car_bookings (status, created_at)';
    END IF;
END
$$;
