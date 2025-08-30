SET SCHEMA 'public';

CREATE SEQUENCE IF NOT EXISTS rss_sequence;

CREATE TABLE public.users
(
    id VARCHAR(30) NOT NULL PRIMARY KEY,
    email VARCHAR(100),
    name VARCHAR(100),
    settings JSONB
);

CREATE TABLE public.channels
(
    id INT NOT NULL PRIMARY KEY DEFAULT NEXTVAL('rss_sequence'),
    title VARCHAR(255),
    link VARCHAR(255)
);

CREATE TABLE public.feeds
(
    link VARCHAR(255) NOT NULL PRIMARY KEY,
    title VARCHAR(255),
    pub_date TIMESTAMP WITH TIME ZONE,
    description TEXT,
    read BOOLEAN,
    channel_id INT,
    CONSTRAINT FK_feeds_channels FOREIGN KEY (channel_id)
        REFERENCES public.channels(id) ON DELETE CASCADE
);

CREATE TABLE public.user_channels
(
    user_id VARCHAR(30) NOT NULL,
    channel_id INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, channel_id),
    CONSTRAINT FK_user_channels_user FOREIGN KEY (user_id) 
        REFERENCES public.users(id) ON DELETE CASCADE,
    CONSTRAINT FK_user_channels_channel FOREIGN KEY (channel_id) 
        REFERENCES public.channels(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_channels_user_channel ON public.user_channels(user_id, channel_id);
