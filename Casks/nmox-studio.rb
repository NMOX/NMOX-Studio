cask "nmox-studio" do
  version "3.4.0"
  sha256 "d352d22356135961e7ca61467cbec6914785df637405766aacfb6d9897489127"

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
