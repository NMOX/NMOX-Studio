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
 * @since 1.55
 */
package org.nmox.studio.core.spi.device;
