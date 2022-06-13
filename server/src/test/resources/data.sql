INSERT INTO user(principal_name, email, settings)
    VALUES('123', 'example@test.com', '{}');
INSERT INTO channel(id, channel_link, title, link, user_principal_name)
    VALUES(1, 'http://test.rss.xml', 'test', 'http://link', '123');
SELECT NEXTVAL('hibernate_sequence');
INSERT INTO feed_item(`guid`, title, link, pub_date, `description`, `read`, channel_id)
    VALUES('guid123', 'test_title', 'http://test.rss.xml', '2022-05-04T22:03:03','test_description', true, 1);
INSERT INTO feed_item(`guid`, title, link, pub_date, `description`, `read`, channel_id)
    VALUES('guid234', 'test_title_2', 'http://test.rss.xml', '2022-05-04T22:03:03','test_description', false, 1);