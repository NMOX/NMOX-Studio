cask "nmox-studio" do
  version "2.155.1"
  sha256 "74a6669e7bb25dfc23a93ccdbca8cf4e5ffec71ee063e2d0a9f3d6305fd069aa"

  url "https://github.com/NMOX/NMOX-Studio/releases/download/v#{version}/NMOX-Studio-#{version}-macos.dmg"
  name "NMOX Studio"
  desc "NetBeans RCP-based IDE for web development"
  homepage "https://github.com/NMOX/NMOX-Studio"

  depends_on macos: :big_sur

  app "NMOX Studio.app"

  # The app is ad-hoc signed, not notarized (no Apple Developer ID yet),
  # and Homebrew 6 removed --no-quarantine — so this cask clears the
  # quarantine attribute itself. Never silently: the caveats below say
  # so at every install, and the trust decision was yours at brew trust.
  postflight_steps do
    run "/usr/bin/xattr",
        args:           ["-dr", "com.apple.quarantine", "{{appdir}}/NMOX Studio.app"],
        writable_paths: ["NMOX Studio.app"],
        writable_base:  :appdir
  end

  zap trash: [
    "~/Library/Application Support/NMOXStudio",
    "~/Library/Caches/org.nmox.studio",
    "~/Library/Preferences/org.nmox.studio.plist",
  ]

  caveats <<~EOS
    Heads up: this app is ad-hoc signed, not notarized (no Apple
    Developer ID yet). Because Homebrew 6 removed --no-quarantine,
    this cask clears macOS's quarantine attribute on the installed
    app itself (postflight_steps above) so first launch works without a
    Gatekeeper refusal. You consented to this third-party tap with
    brew trust; the DMG comes over HTTPS from the project's GitHub
    releases and is pinned by the sha256 above.

    Installing from the DMG by hand instead? First launch needs
    right-click > Open once, or:
      xattr -dr com.apple.quarantine "/Applications/NMOX Studio.app"

    After first launch, the in-app updater (Tools > Plugins) keeps
    you current with no Gatekeeper involvement at all.
  EOS
end
