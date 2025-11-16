-- Migration to add highlighted column to user_channels table
-- This allows users to highlight specific channels with a visual indicator

ALTER TABLE public.user_channels ADD COLUMN highlighted BOOLEAN NOT NULL DEFAULT FALSE;
