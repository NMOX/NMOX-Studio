cask "nmox-studio" do
  version "2.113.0"
  sha256 "ae8c9668ae6b6c336ac03f869e68ee2de3193c3a8cba36c9ae71a2293e34ecd1"

  url "https://github.com/NMOX/NMOX-Studio/releases/download/v#{version}/NMOX-Studio-#{version}-macos.dmg",
      verified: "github.com/NMOX/NMOX-Studio/"
  name "NMOX Studio"
  desc "NetBeans RCP-based IDE for web development"
  homepage "https://github.com/NMOX/NMOX-Studio"

  depends_on macos: :big_sur

  app "NMOX Studio.app"

  # The app is ad-hoc signed, not notarized (no Apple Developer ID yet),
  # and Homebrew 6 removed --no-quarantine — so this cask clears the
  # quarantine attribute itself. Never silently: the caveats below say
  # so at every install, and the trust decision was yours at brew trust.
  postflight do
    system_command "/usr/bin/xattr",
                   args: ["-dr", "com.apple.quarantine", "#{appdir}/NMOX Studio.app"],
                   sudo: false
  end

  caveats <<~EOS
    Heads up: this app is ad-hoc signed, not notarized (no Apple
    Developer ID yet). Because Homebrew 6 removed --no-quarantine,
    this cask clears macOS's quarantine attribute on the installed
    app itself (postflight above) so first launch works without a
    Gatekeeper refusal. You consented to this third-party tap with
    brew trust; the DMG comes over HTTPS from the project's GitHub
    releases and is pinned by the sha256 above.

    Installing from the DMG by hand instead? First launch needs
    right-click > Open once, or:
      xattr -dr com.apple.quarantine "/Applications/NMOX Studio.app"

    After first launch, the in-app updater (Tools > Plugins) keeps
    you current with no Gatekeeper involvement at all.
  EOS

  zap trash: [
    "~/Library/Application Support/NMOXStudio",
    "~/Library/Caches/org.nmox.studio",
    "~/Library/Preferences/org.nmox.studio.plist",
  ]
end
