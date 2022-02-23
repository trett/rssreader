package ru.trett.rss;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import javax.validation.constraints.NotNull;
import java.util.Collections;

public class CustomAuthenticationToken extends AbstractAuthenticationToken {

    private final String sub;
    private final String email;

    public CustomAuthenticationToken(@NonNull String sub,
                                     @NotNull String email) {
        super(Collections.emptyList());
        this.sub = sub;
        this.email = email;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return sub;
    }

    public String getEmail() {
        return email;
    }
}
