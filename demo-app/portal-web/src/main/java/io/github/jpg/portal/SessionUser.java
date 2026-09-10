package io.github.jpg.portal;

import javax.enterprise.context.SessionScoped;
import javax.inject.Named;
import java.io.Serializable;

/**
 * The signed-in user for the current HTTP session. Deliberately minimal —
 * this demo is about metrics, not authentication.
 */
@Named
@SessionScoped
public class SessionUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;

    public boolean isLoggedIn() {
        return username != null;
    }

    public String getUsername() {
        return username;
    }

    void signIn(String username) {
        this.username = username;
    }

    void signOut() {
        this.username = null;
    }
}
