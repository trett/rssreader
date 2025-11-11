-- Migration to support per-user read states for feeds
-- This allows the same feed to appear in multiple users' channels with independent read states

-- Add user_id column to feeds table
ALTER TABLE public.feeds ADD COLUMN user_id VARCHAR(30);

-- Populate user_id from existing user_channels relationships
UPDATE public.feeds f
SET user_id = (
    SELECT uc.user_id
    FROM public.user_channels uc
    WHERE uc.channel_id = f.channel_id
    LIMIT 1
);

-- Make user_id NOT NULL after populating
ALTER TABLE public.feeds ALTER COLUMN user_id SET NOT NULL;

-- Drop the old primary key constraint
ALTER TABLE public.feeds DROP CONSTRAINT feeds_pkey;

-- Create new composite primary key on (link, user_id)
ALTER TABLE public.feeds ADD PRIMARY KEY (link, user_id);

-- Add foreign key constraint for user_id
ALTER TABLE public.feeds ADD CONSTRAINT FK_feeds_users 
    FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

-- Create index for better query performance
CREATE INDEX idx_feeds_user_channel ON public.feeds(user_id, channel_id);
CREATE INDEX idx_feeds_channel_read ON public.feeds(channel_id, read);
