package org.nmox.studio.core.spi.device;

/**
 * A third-party rack device. Register an implementation with
 * {@code @ServiceProvider(service = DeviceExtension.class)} in any
 * installed module and the device appears on the palette shelf, mounts
 * by drag or Quick Search, persists in patches, and is held to the same
 * contract laws as the built-in fleet.
 *
 * <pre>{@code
 * @ServiceProvider(service = DeviceExtension.class)
 * public class UptimeDevice implements DeviceExtension {
 *
 *     public DeviceDescriptor descriptor() {
 *         return new DeviceDescriptor(
 *                 "com.example.uptime", "UPTIME", "Host uptime on a button",
 *                 new Color(120, 180, 220), DeviceCategory.OBSERVE,
 *                 "CHECK runs uptime and shows the load line on the LCD.\n"
 *                         + "Patch OK onward to chain on success.",
 *                 1, List.of(new PortSpec("ok", "OK",
 *                         PortSpec.Direction.OUT, PortSpec.Signal.TRIGGER)));
 *     }
 *
 *     public DeviceLogic build(DeviceFace face, DeviceServices services) {
 *         var screen = face.lcd("status", 300, 1);
 *         var check = face.button("CHECK", DeviceFace.ButtonRole.QUERY);
 *         check.onPress(() -> services.exec(List.of("uptime"),
 *                 screen::setText,
 *                 code -> services.emitTrigger("ok", code == 0)));
 *         return new DeviceLogic() { };
 *     }
 * }
 * }</pre>
 *
 * <p>{@link #build} is called once per mounted instance, on the EDT;
 * it must not block. A validation failure in the descriptor (missing
 * dot in the id, blank usage, duplicate port ids…) is logged and the
 * extension is skipped — one bad plugin never breaks the shelf.
 *
 * @since 1.55
 */
public interface DeviceExtension {

    /** The device's identity card; called often, must be cheap and stable. */
    DeviceDescriptor descriptor();

    /** Builds one mounted instance's face and returns its behavior. */
    DeviceLogic build(DeviceFace face, DeviceServices services);
}
