-- Migration to support per-user read states for feeds
-- This allows the same feed to appear in multiple users' channels with independent read states

-- Add user_id column to feeds table
ALTER TABLE public.feeds ADD COLUMN user_id VARCHAR(30);

-- Populate user_id from existing user_channels relationships
-- This handles feeds that may appear in multiple channels by duplicating them per user
-- Step 1: Create a temporary table to store all required feed rows
CREATE TEMP TABLE feeds_new AS
SELECT
    f.link,
    uc.user_id,
    f.channel_id,
    f.title,
    f.description,
    f.pub_date,
    f.read
FROM public.feeds f
JOIN public.user_channels uc ON uc.channel_id = f.channel_id;

-- Step 2: Delete all original feeds
DELETE FROM public.feeds;

-- Step 3: Drop the old primary key constraint (on just link)
ALTER TABLE public.feeds DROP CONSTRAINT feeds_pkey;

-- Step 4: Make user_id NOT NULL
ALTER TABLE public.feeds ALTER COLUMN user_id SET NOT NULL;

-- Step 5: Create new composite primary key on (link, user_id)
ALTER TABLE public.feeds ADD PRIMARY KEY (link, user_id);

-- Step 6: Add foreign key constraint for user_id
ALTER TABLE public.feeds ADD CONSTRAINT FK_feeds_users 
    FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

-- Step 7: Insert duplicated feeds with user_id
INSERT INTO public.feeds (link, user_id, channel_id, title, description, pub_date, read)
SELECT link, user_id, channel_id, title, description, pub_date, read FROM feeds_new;

-- Step 8: Drop the temporary table
DROP TABLE feeds_new;

-- Create indexes for better query performance
CREATE INDEX idx_feeds_user_channel ON public.feeds(user_id, channel_id);
CREATE INDEX idx_feeds_channel_user_read ON public.feeds(channel_id, user_id, read);
