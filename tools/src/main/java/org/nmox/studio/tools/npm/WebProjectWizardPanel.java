package org.nmox.studio.tools.npm;

import java.awt.Component;
import java.io.File;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.WizardValidationException;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;

public class WebProjectWizardPanel implements WizardDescriptor.Panel<WizardDescriptor>,
        WizardDescriptor.ValidatingPanel<WizardDescriptor>, WizardDescriptor.FinishablePanel<WizardDescriptor> {

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
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
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
        // Notify listeners of changes
    }
}