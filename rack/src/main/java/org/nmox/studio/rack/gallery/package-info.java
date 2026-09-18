/**
 * The rack gallery's catalog: every rack the product can hand a user, on
 * one shelf (v2.179.0). No Swing lives here — a window reads this package,
 * and everything in it is documented "call off the EDT" because it reads
 * disk, the classpath and the PATH.
 *
 * <p>Until this package a rack reached a user four ways with nothing in
 * common: the code-built presets
 * ({@link org.nmox.studio.rack.projectstudio.RackPresets}), the starters a
 * project kind is born with
 * ({@link org.nmox.studio.rack.projectstudio.StarterRacks}), the user's own
 * drop-ins in {@code ~/.nmox/presets.d}
 * ({@link org.nmox.studio.rack.projectstudio.UserPresets}) and files passed
 * between people ({@link org.nmox.studio.rack.model.RackShare}).
 * {@link org.nmox.studio.rack.gallery.RackGallery} lists them all as one
 * kind of thing — an {@code Entry} with a
 * {@link org.nmox.studio.rack.model.RackCard}, its devices, a readable
 * wiring sketch and whether it fits the aimed project — and adds the fifth
 * source this package owns: the COMMUNITY racks.
 *
 * <p>The community racks ship inside the module as resources
 * ({@code racks/index} names them, the {@code BundledDevices} idiom, because
 * a classpath cannot be listed portably). That directory is the ONE home and
 * the place a contributor adds a rack by pull request.
 * {@link org.nmox.studio.rack.gallery.RackJudge} is the law such a rack must
 * meet — it names itself, uses only built-in devices, really MOUNTS with
 * every cable, arrives at rest, carries no path or address from its author's
 * machine — and it is used twice: by {@code CommunityRacksGateTest}, which
 * fails the build by file name, and by the runtime loader, which skips a bad
 * rack with its reason logged so one bad file can never break the gallery.
 *
 * <p>Reading order: {@code RackGallery} (the API a window builds against),
 * {@code RackJudge} (the rules), {@code RackWiring} (the pure sketch),
 * {@code CommunityRacks} (the loader). {@code docs/racks.md} is generated
 * from this catalog by {@code RackGalleryDocsTest}, the way
 * {@code docs/devices.md} is generated from the device catalog.
 */
package org.nmox.studio.rack.gallery;
