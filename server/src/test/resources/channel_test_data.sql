ALTER SEQUENCE hibernate_sequence RESTART WITH 1;
INSERT INTO public.user(principal_name, email, settings)
    VALUES('123', 'example@test.com', '{}');
INSERT INTO public.channel(id, channel_link, title, link, user_principal_name)
    VALUES(1, 'http://channel_link.local', 'title_1', 'http://link.local', '123');
SELECT NEXTVAL('hibernate_sequence');