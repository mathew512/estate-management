-- V4__init_billing_schema.sql

CREATE TABLE meter_readings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_id UUID NOT NULL REFERENCES units(id),
    reading_date DATE NOT NULL,
    previous_reading NUMERIC(10, 2) NOT NULL,
    current_reading NUMERIC(10, 2) NOT NULL,
    rate_per_unit NUMERIC(8, 2) NOT NULL,
    total_charge NUMERIC(10, 2) GENERATED ALWAYS AS ((current_reading - previous_reading) * rate_per_unit) STORED,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lease_id UUID NOT NULL REFERENCES leases(id),
    period_month DATE NOT NULL,
    rent_amount NUMERIC(12, 2) NOT NULL,
    utility_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    penalty_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(12, 2) GENERATED ALWAYS AS (rent_amount + utility_amount + penalty_amount) STORED,
    amount_paid NUMERIC(12, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    due_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_meter_readings_unit_id ON meter_readings(unit_id);
CREATE INDEX idx_invoices_lease_id ON invoices(lease_id);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_period_month ON invoices(period_month);