package io.github.jpg.portal;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.Serializable;

/**
 * Backs {@code login.xhtml}. Accepts any non-blank username with the password
 * {@code test}, then redirects to the discharge list. A small artificial delay
 * simulates a real authentication round-trip so the login timing is visible in
 * a load test.
 */
@Named
@ViewScoped
public class LoginBean implements Serializable {

    private static final long serialVersionUID = 1L;
    static final String PASSWORD = "test";

    private String username;
    private String password;

    @Inject
    private SessionUser sessionUser;

    public String login() {
        simulateWork(120);
        if (username != null && !username.isBlank() && PASSWORD.equals(password)) {
            sessionUser.signIn(username.trim());
            return "/secure/discharges.xhtml?faces-redirect=true";
        }
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, "Sign in failed",
                        "Check the username and password (hint: password is 'test')."));
        return null;
    }

    public void logout() throws IOException {
        sessionUser.signOut();
        FacesContext ctx = FacesContext.getCurrentInstance();
        ctx.getExternalContext().invalidateSession();
        ctx.getExternalContext().redirect(ctx.getExternalContext().getRequestContextPath() + "/login.xhtml");
    }

    private static void simulateWork(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
