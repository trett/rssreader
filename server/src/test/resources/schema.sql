CREATE TABLE public.users
(
    `principal_name` varchar(255) NOT NULL PRIMARY KEY,
    `email` varchar(255),
    `settings` varchar(255)
);

CREATE TABLE public.channels
(
    `id` int NOT NULL PRIMARY KEY AUTO_INCREMENT,
    `channel_link` varchar(255),
    `title` varchar(255),
    `link` varchar(255),
    `user_principal_name` varchar(255)
);

ALTER TABLE public.channels
    ADD CONSTRAINT FK_channels_users FOREIGN KEY (user_principal_name)
    REFERENCES public.users(principal_name);

CREATE TABLE public.feeds
(
    `id` int NOT NULL PRIMARY KEY AUTO_INCREMENT,
    `guid` varchar(255),
    `title` varchar(255),
    `link` varchar(255),
    `pub_date` timestamp,
    `description` varchar(255),
    `read` boolean,
    `channel_id` int
);

ALTER TABLE feeds
    ADD CONSTRAINT FK_feeds_channels FOREIGN KEY (channel_id)
    REFERENCES public.channels(id);