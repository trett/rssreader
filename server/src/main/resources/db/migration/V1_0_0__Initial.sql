SET SCHEMA 'public';

CREATE SEQUENCE IF NOT EXISTS rss_sequence;

CREATE TABLE public.user
(
    principal_name varchar(100) NOT NULL PRIMARY KEY,
    email varchar(100),
    settings varchar(255)
);

CREATE TABLE public.channel
(
    id INT NOT NULL PRIMARY KEY DEFAULT NEXTVAL('rss_sequence'),
    channel_link varchar(255),
    title varchar(255),
    link varchar(255),
    user_principal_name varchar(100)
);

ALTER TABLE public.channel ADD CONSTRAINT FK_channel_user FOREIGN KEY (user_principal_name) REFERENCES public.user(principal_name);

CREATE TABLE public.feed_item
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

ALTER TABLE public.feed_item ADD CONSTRAINT FK_feed_item_channel FOREIGN KEY (channel_id) REFERENCES public.channel(id);