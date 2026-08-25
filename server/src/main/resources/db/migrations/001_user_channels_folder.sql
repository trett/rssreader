-- Sidebar folders: per-user grouping of channels.
-- Already included in db/init.sql; this file is for databases created before it.
-- Safe to run more than once.

ALTER TABLE public.user_channels
    ADD COLUMN IF NOT EXISTS folder VARCHAR(100);
