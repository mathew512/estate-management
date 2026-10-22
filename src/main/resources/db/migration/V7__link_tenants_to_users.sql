-- V7__link_tenants_to_users.sql

ALTER TABLE tenants ADD COLUMN user_id UUID UNIQUE REFERENCES users(id);