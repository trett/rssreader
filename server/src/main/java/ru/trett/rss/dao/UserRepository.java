package ru.trett.rss.dao;

import org.springframework.data.repository.CrudRepository;

import ru.trett.rss.models.User;

public interface UserRepository extends CrudRepository<User, Long> {

    User findByPrincipalName(String name);
}
