cask "nmox-studio" do
  version "3.8.0"
  sha256 "0f23b3d63f1679d6b47e0764e95f708fa9083fe4a2faa79a63c9193d5451342d"

  url "https://github.com/NMOX/NMOX-Studio/releases/download/v#{version}/NMOX-Studio-#{version}-macos.dmg"
  name "NMOX Studio"
  desc "NetBeans RCP-based IDE for web development"
  homepage "https://github.com/NMOX/NMOX-Studio"

  depends_on :macos

  app "NMOX Studio.app"
  binary "#{appdir}/NMOX Studio.app/Contents/MacOS/nmox-studio", target: "nmox"

  zap trash: [
    "~/Library/Application Support/NMOXStudio",
    "~/Library/Caches/org.nmox.studio",
    "~/Library/Preferences/org.nmox.studio.plist",
  ]

  caveats <<~EOS
    After first launch, the in-app updater (Tools > Plugins) keeps
    you current with no Gatekeeper involvement at all.
  EOS
end
