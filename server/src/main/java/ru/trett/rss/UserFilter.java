package ru.trett.rss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import ru.trett.rss.dao.UserRepository;
import ru.trett.rss.models.Settings;
import ru.trett.rss.models.User;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class UserFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserFilter.class);
    private static final Map<String, User> userCache = new ConcurrentHashMap<>();
    private final UserRepository userRepository;

    @Autowired
    public UserFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CustomAuthenticationToken authentication =
                (CustomAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            String name = (String) authentication.getPrincipal();
            LOGGER.info("Loading user with sub: " + name + " from cache");
            User cachedUser = userCache.get(name);
            if (cachedUser == null) {
                LOGGER.info("Loading user with sub: " + name);
                User user = userRepository.findByPrincipalName(name);

                if (user == null) {
                    user = saveUser(name, authentication.getEmail());
                }

                userCache.put(name, user);
            }
        }
        chain.doFilter(request, response);
    }

    private User saveUser(String name, String email) {
        LOGGER.info("Creating user with sub: " + name);
        User user = new User(name, email);
        user.setSettings(new Settings());
        userRepository.save(user);
        LOGGER.info("User with sub: " + name + " has been saved");
        return user;
    }
}
