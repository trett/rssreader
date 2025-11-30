-- Migration to increase VARCHAR limits for feeds and channels
-- RSS feed titles and links can be much longer than 255 characters

-- Increase link and title length limits for channels
ALTER TABLE public.channels ALTER COLUMN link TYPE VARCHAR(500);
ALTER TABLE public.channels ALTER COLUMN title TYPE VARCHAR(1000);

-- Increase link and title length limits for feeds
ALTER TABLE public.feeds ALTER COLUMN link TYPE VARCHAR(500);
ALTER TABLE public.feeds ALTER COLUMN title TYPE VARCHAR(1000);
