SET SCHEMA 'public';

CREATE SEQUENCE IF NOT EXISTS rss_sequence;

CREATE TABLE public.users
(
    principal_name varchar(100) NOT NULL PRIMARY KEY,
    email varchar(100),
    settings varchar(255)
);

CREATE TABLE public.channels
(
    id int NOT NULL PRIMARY KEY DEFAULT NEXTVAL('rss_sequence'),
    channel_link varchar(255),
    title varchar(255),
    link varchar(255),
    user_principal_name varchar(100)
);

ALTER TABLE public.channels
    ADD CONSTRAINT FK_channel_users FOREIGN KEY (user_principal_name)
    REFERENCES public.users(principal_name);

CREATE TABLE public.feeds
(
    id int NOT NULL PRIMARY KEY DEFAULT NEXTVAL('rss_sequence'),
    guid varchar(255),
    title varchar(255),
    link varchar(255),
    pub_date timestamp,
    description text,
    read boolean,
    channel_id int
);

ALTER TABLE public.feeds
    ADD CONSTRAINT FK_feeds_channels FOREIGN KEY (channel_id)
    REFERENCES public.channels(id);