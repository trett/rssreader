package com.trett.rss;

import com.trett.rss.dao.UserRepository;
import com.trett.rss.models.Settings;
import com.trett.rss.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@Component
public class SuccessHandler implements AuthenticationSuccessHandler {

    private OAuth2AuthorizedClientService authorizedClientService;

    private UserRepository userRepository;

    @Autowired
    public SuccessHandler(OAuth2AuthorizedClientService authorizedClientService, UserRepository userRepository) {
        this.authorizedClientService = authorizedClientService;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                                        Authentication authentication) throws IOException, ServletException {
        String principalName = authentication.getName();
        OAuth2AuthorizedClient client = authorizedClientService
                .loadAuthorizedClient(((OAuth2AuthenticationToken) authentication)
                        .getAuthorizedClientRegistrationId(), authentication.getName());
        String userInfoEndpointUri = client.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri();
        if (!StringUtils.isEmpty(userInfoEndpointUri)) {
            RestTemplate template = new RestTemplate();
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue());
            HttpEntity entity = new HttpEntity("", httpHeaders);
            ResponseEntity<Map> response = template.exchange(userInfoEndpointUri, HttpMethod.GET, entity, Map.class);
            Map userAttributes = response.getBody();
            if (userAttributes == null) {
                throw new RuntimeException("Can't find email");
            }
            User user = userRepository.findByPrincipalName(authentication.getName());
            if (user == null) {
                user = new User(principalName, (String) userAttributes.get("email"));
                user.setSettings(new Settings());
                userRepository.save(user);
            }
        }
        httpServletResponse.sendRedirect("/");
    }
}
