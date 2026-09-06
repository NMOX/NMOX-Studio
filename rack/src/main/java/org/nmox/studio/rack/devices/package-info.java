/**
 * The device fleet — one class per faceplate (IGNITION, CRATE,
 * VERITAS, MONITOR, KVASIR …), each a
 * {@link org.nmox.studio.rack.model.RackDevice} subclass that declares
 * its knobs/buttons/LEDs/LCD and reacts to signals. If you are new,
 * open one small device (e.g. {@code TempoDevice}) next to the product
 * window and match code to faceplate.
 *
 * <p>Since v2.0.0 this package also hosts the <b>programmable rack</b>:
 * {@link org.nmox.studio.rack.devices.DeviceFile} is a pure judge that
 * parses a JSON device definition and REFUSES anything the format must
 * not express (shell metacharacters, tool paths, unknown variables);
 * {@code UserDevices} reads {@code ~/.nmox/devices.d/} drop-ins;
 * {@code BundledDevices} ships a gallery inside the jar; and
 * {@code JsonDeviceExtension} adapts a parsed file onto the frozen
 * Device SPI so the HOST enforces every law (trust gate, color law,
 * accessible names) — the JSON cannot opt out. {@code DeviceCatalog}
 * merges built-ins, gallery, Lookup plugins and drop-ins into the one
 * registry the palette, persistence and ⌘I all consult.
 */
package org.nmox.studio.rack.devices;
