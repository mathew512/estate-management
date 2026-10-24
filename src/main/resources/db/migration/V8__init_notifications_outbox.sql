-- V8__init_notifications_outbox.sql

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel VARCHAR(10) NOT NULL,
    recipient VARCHAR(150) NOT NULL,
    subject VARCHAR(200),
    body TEXT NOT NULL,
    type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(255),
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE,
    dedupe_key VARCHAR(150) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_due ON notifications(next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_notifications_status ON notifications(status);