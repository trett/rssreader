package com.trett.rss;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CustomEntryPoint extends LoginUrlAuthenticationEntryPoint {

    private static final String XML_HTTP_REQUEST = "XMLHttpRequest";

    private static final String X_REQUESTED_WITH = "X-Requested-With";

    CustomEntryPoint(String loginFormUrl) {
        super(loginFormUrl);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        if (XML_HTTP_REQUEST.equals(request.getHeader(X_REQUESTED_WITH))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            super.commence(request, response, exception);
        }
    }
}
