package io.github.jpg.portal;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * Backs {@code secure/discharge.xhtml} — the electronic discharge form.
 */
@Named
@ViewScoped
public class DischargeFormBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private DischargeStore store;

    @Inject
    private SessionUser sessionUser;

    private Discharge discharge;

    @PostConstruct
    void init() {
        discharge = new Discharge();
        discharge.setAdmissionDate(LocalDate.now().minusDays(3));
        discharge.setDischargeDate(LocalDate.now());
    }

    public String save() {
        discharge.setCreatedBy(sessionUser.getUsername());
        store.save(discharge);
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, "Discharge saved",
                        "Reference " + discharge.getPatientReference()));
        return "/secure/discharges.xhtml?faces-redirect=true";
    }

    public Discharge getDischarge() {
        return discharge;
    }
}
