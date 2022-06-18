CREATE SEQUENCE IF NOT EXISTS hibernate_sequence;

CREATE TABLE user
(
    `principal_name` varchar(255) NOT NULL PRIMARY KEY,
    `email` varchar(255),
    `settings` varchar(255)
);

CREATE TABLE channel
(
    `id` int DEFAULT NOT NULL PRIMARY KEY,
    `channel_link` varchar(255),
    `title` varchar(255),
    `link` varchar(255),
    `user_principal_name` varchar(255)
);

ALTER TABLE channel ADD CONSTRAINT FK_channel_user FOREIGN KEY (user_principal_name) REFERENCES user(principal_name);

CREATE TABLE feed_item
(
    `id` int DEFAULT NOT NULL PRIMARY KEY,
    `guid` varchar(255),
    `title` varchar(255),
    `link` varchar(255),
    `pub_date` timestamp,
    `description` varchar(255),
    `read` boolean,
    `channel_id` int
);

ALTER TABLE feed_item ADD CONSTRAINT FK_feed_item_channel FOREIGN KEY (channel_id) REFERENCES channel(id);