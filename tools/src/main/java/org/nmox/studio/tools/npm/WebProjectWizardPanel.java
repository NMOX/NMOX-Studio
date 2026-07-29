package org.nmox.studio.tools.npm;

import java.awt.Component;
import java.io.File;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.WizardValidationException;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;

/**
 * The single step of the New Web Project wizard: the controller half of
 * the platform's panel/visual split. The wizard machinery drives this
 * {@link WizardDescriptor.Panel} — {@code readSettings} pushes the
 * descriptor's properties into the Swing form ({@link
 * WebProjectWizardPanelVisual}, created lazily on first
 * {@code getComponent}), {@code storeSettings} pulls name/location/type
 * back out, and {@code isValid} delegates to the form's checks so Finish
 * greys honestly. The visual calls {@code fireChangeEvent} on every
 * keystroke, which is how the wizard learns to re-ask {@code isValid}.
 * Finishable because this is the only step.
 */
public class WebProjectWizardPanel implements WizardDescriptor.Panel<WizardDescriptor>,
        WizardDescriptor.ValidatingPanel<WizardDescriptor>, WizardDescriptor.FinishablePanel<WizardDescriptor> {

    private final org.openide.util.ChangeSupport changeSupport =
            new org.openide.util.ChangeSupport(this);
    private WizardDescriptor wizardDescriptor;
    private WebProjectWizardPanelVisual component;

    @Override
    public Component getComponent() {
        if (component == null) {
            component = new WebProjectWizardPanelVisual(this);
            component.setName(NbBundle.getMessage(WebProjectWizardPanel.class, "LBL_CreateProjectStep"));
        }
        return component;
    }

    @Override
    public HelpCtx getHelp() {
        return new HelpCtx("org.nmox.studio.tools.npm.WebProjectWizardPanel");
    }

    @Override
    public boolean isValid() {
        getComponent();
        return component.valid(wizardDescriptor);
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        changeSupport.addChangeListener(l);
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        changeSupport.removeChangeListener(l);
    }

    @Override
    public void readSettings(WizardDescriptor wiz) {
        wizardDescriptor = wiz;
        component.read(wizardDescriptor);
    }

    @Override
    public void storeSettings(WizardDescriptor wiz) {
        WizardDescriptor d = wiz;
        component.store(d);
    }

    @Override
    public boolean isFinishPanel() {
        return true;
    }

    @Override
    public void validate() throws WizardValidationException {
        getComponent();
        component.validate(wizardDescriptor);
    }

    void fireChangeEvent() {
        changeSupport.fireChange();
    }
}