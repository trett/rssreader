-- ALTER SEQUENCE rss_sequence RESTART WITH 1;
INSERT INTO public.users(principal_name, email, settings)
    VALUES('123', 'example@test.com', '{}');
INSERT INTO public.channels(channel_link, title, link, user_principal_name)
    VALUES('http://channel_link.local', 'title_1', 'http://link.local', '123');
INSERT INTO public.feeds(`guid`, title, link, pub_date, `description`, `read`, channel_id)
    VALUES('guid123', 'test_title', 'http://test.rss.xml', '2022-05-04T22:03:03','test_description', true, 1);