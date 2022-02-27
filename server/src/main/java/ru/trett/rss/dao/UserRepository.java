package ru.trett.rss.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import ru.trett.rss.models.User;

@Repository
public interface UserRepository extends CrudRepository<User, Long> {

    User findByPrincipalName(String name);
}
