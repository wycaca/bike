CREATE TABLE ride_order (
    order_id VARCHAR(48) PRIMARY KEY,
    vehicle_id VARCHAR(32) NOT NULL REFERENCES vehicle(vehicle_id),
    rider_id VARCHAR(40) NOT NULL,
    city_code VARCHAR(6) NOT NULL,
    area_code VARCHAR(6) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    duration_seconds INTEGER NOT NULL DEFAULT 0 CHECK (duration_seconds >= 0),
    distance_meters INTEGER NOT NULL DEFAULT 0 CHECK (distance_meters >= 0),
    gross_amount NUMERIC(10, 2) NOT NULL CHECK (gross_amount >= 0),
    discount_amount NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    refund_amount NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (refund_amount >= 0),
    net_revenue NUMERIC(10, 2) GENERATED ALWAYS AS
        (gross_amount - discount_amount - refund_amount) STORED,
    order_status VARCHAR(24) NOT NULL CHECK (
        order_status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED', 'CANCELLED')
    ),
    payment_channel VARCHAR(16) CHECK (payment_channel IN ('WECHAT', 'ALIPAY')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (discount_amount + refund_amount <= gross_amount),
    CHECK (
        (order_status = 'CANCELLED' AND ended_at IS NULL)
        OR (order_status <> 'CANCELLED' AND ended_at > started_at)
    )
);

CREATE INDEX ride_order_city_time_idx
    ON ride_order (city_code, started_at, order_status);
CREATE INDEX ride_order_vehicle_time_idx
    ON ride_order (vehicle_id, started_at DESC);
CREATE INDEX ride_order_rider_time_idx
    ON ride_order (rider_id, started_at DESC);
