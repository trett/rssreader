package com.trett.rss.dao;

import com.trett.rss.models.User;
import org.springframework.data.repository.CrudRepository;


public interface UserRepository extends CrudRepository<User, Long> {

    User findByPrincipalName(String name);
}
