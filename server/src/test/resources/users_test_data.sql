-- ALTER SEQUENCE hibernate_sequence RESTART WITH 1;
INSERT INTO public.user(principal_name, email, settings)
    VALUES('123', 'example@test.com', '{}');
INSERT INTO public.user(principal_name, email, settings)
    VALUES('234', 'example2@test.com', '{"deleteAfter": 7, "hideRead": false}');