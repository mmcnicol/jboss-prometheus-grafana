package io.github.jpg.portal;

import javax.annotation.PostConstruct;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.List;

/**
 * Backs {@code secure/discharges.xhtml} — the read/list page.
 */
@Named
@ViewScoped
public class DischargeListBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private DischargeStore store;

    private List<Discharge> discharges;

    @PostConstruct
    void load() {
        discharges = store.findAll();
    }

    public List<Discharge> getDischarges() {
        return discharges;
    }

    public int getCount() {
        return discharges == null ? 0 : discharges.size();
    }
}
