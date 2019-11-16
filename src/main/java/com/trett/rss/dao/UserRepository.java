package com.trett.rss.dao;

import com.trett.rss.models.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends CrudRepository<User, Long> {

    User findByPrincipalName(String name);
}
