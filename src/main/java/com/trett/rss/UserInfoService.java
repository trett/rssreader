package com.trett.rss;

import com.trett.rss.dao.UserRepository;
import com.trett.rss.models.Settings;
import com.trett.rss.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class UserInfoService extends OidcUserService {

    private Logger logger = LoggerFactory.getLogger(OidcUserService.class);

    @Autowired
    private UserRepository userRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        Map attributes = oidcUser.getAttributes();
        String principalName = (String) attributes.get("sub");
        logger.info("Loading user with sub: " + principalName);
        User user = userRepository.findByPrincipalName(principalName);
        if (user == null) {
            user = new User(principalName, (String) attributes.get("email"));
            user.setSettings(new Settings());
            userRepository.save(user);
        }
        return oidcUser;
    }
}
