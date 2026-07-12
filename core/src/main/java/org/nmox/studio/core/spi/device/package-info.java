/**
 * The public Device SPI: third-party NetBeans modules contribute rack
 * devices by registering a {@link org.nmox.studio.core.spi.device.DeviceExtension}
 * via {@code @ServiceProvider}. The contract is deliberately declarative —
 * a descriptor names the device and its ports, a face builder assembles
 * the control surface from the rack's own widget vocabulary, and a logic
 * object receives signals — so the rack HOST enforces the house laws
 * (workspace trust before any process, the color law via roles, mandatory
 * accessible names, the transport columns) rather than trusting plugin
 * code to follow them.
 *
 * <p><b>This package is frozen API.</b> Evolution is additive only:
 * new methods arrive as interface {@code default}s, records never lose
 * components. Plugins declare a dependency on the core module and are
 * refused by the module loader on an install too old for them.
 *
 * <p><b>Note on adding kinds:</b> {@link PortSpec.Signal} and
 * {@link DeviceCategory} are the SPI's names for the rack's internal
 * signal types and palette shelves; the host maps them by name. Adding a
 * new signal kind or category to this package therefore also requires
 * host work (a matching internal constant and switch arm) in the same
 * release — the two are frozen in lockstep, unlike a new control method
 * on {@link DeviceFace} or a new callback on {@link DeviceLogic}, which
 * are purely additive.
 *
 * @since 1.55
 */
package org.nmox.studio.core.spi.device;
