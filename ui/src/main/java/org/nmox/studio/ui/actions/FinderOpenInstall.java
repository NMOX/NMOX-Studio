package org.nmox.studio.ui.actions;

import org.openide.modules.ModuleInstall;

/**
 * The earliest moment module code runs: {@link #validate()}.
 *
 * <p>{@code validate()} exists to veto a module, and this one never
 * vetoes; it is used because of WHEN it runs. The module manager calls
 * {@code NbInstaller.prepare}, and through it every declaring module's
 * {@code validate()}, for the whole batch before it calls a single
 * {@code restored()} ({@code ModuleManager.enable}: prepare, then
 * classLoaderUp, then load; read from the RELEASE310 bytecode). The
 * platform's applemenu module sets its open-files handler in
 * {@code restored()}, and the JDK hands the event that launched the app
 * to whichever handler is set FIRST, so this is the one place a folder
 * dropped on a closed NMOX Studio can be caught. An {@code @OnStart}
 * runnable would race: those are posted to a request processor from
 * inside {@code load}, concurrently with the {@code restored()} calls.
 *
 * <p>Everything it does is cheap and cannot fail the module: see
 * {@link FinderOpen#early()}.
 */
public final class FinderOpenInstall extends ModuleInstall {

    private static final long serialVersionUID = 1L;

    @Override
    public void validate() {
        FinderOpen.early();
    }
}
