/**
 * One "NMOX Studio" category in Tools &gt; Options: every product
 * setting registers as a subpanel here instead of scattering across
 * top-level categories.
 */
@OptionsPanelController.ContainerRegistration(
        id = "NmoxStudio",
        categoryName = "#OptionsCategory_Name_NmoxStudio",
        iconBase = "org/nmox/studio/rack/options/rack32.png",
        keywords = "#OptionsCategory_Keywords_NmoxStudio",
        keywordsCategory = "NmoxStudio")
@org.openide.util.NbBundle.Messages({
    "OptionsCategory_Name_NmoxStudio=NMOX Studio",
    "OptionsCategory_Keywords_NmoxStudio=nmox studio rack cloud format"
})
package org.nmox.studio.rack.options;

import org.netbeans.spi.options.OptionsPanelController;
