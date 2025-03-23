SET SCHEMA 'public';

CREATE SEQUENCE IF NOT EXISTS rss_sequence;

CREATE TABLE public.users
(
    id varchar(30) NOT NULL PRIMARY KEY,
    email varchar(100),
    name varchar(100),
    settings varchar(255)
);

CREATE TABLE public.channels
(
    id int NOT NULL PRIMARY KEY DEFAULT NEXTVAL('rss_sequence'),
    title varchar(255),
    link varchar(255)
);

CREATE TABLE public.feeds
(
    id int NOT NULL PRIMARY KEY DEFAULT NEXTVAL('rss_sequence'),
    title varchar(255),
    link varchar(255),
    pub_date timestamp with time zone,
    description text,
    read boolean,
    channel_id int
);

ALTER TABLE public.feeds
    ADD CONSTRAINT FK_feeds_channels FOREIGN KEY (channel_id)
    REFERENCES public.channels(id);

CREATE TABLE public.user_channels
(
    user_id varchar(30) NOT NULL,
    channel_id int NOT NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, channel_id),
    CONSTRAINT FK_user_channels_user FOREIGN KEY (user_id) 
        REFERENCES public.users(id) ON DELETE CASCADE,
    CONSTRAINT FK_user_channels_channel FOREIGN KEY (channel_id) 
        REFERENCES public.channels(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_channels_user ON public.user_channels(user_id);
CREATE INDEX idx_user_channels_channel ON public.user_channels(channel_id);