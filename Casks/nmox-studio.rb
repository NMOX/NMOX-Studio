cask "nmox-studio" do
  version "3.5.1"
  sha256 "e5cd9ab385671959035bbf84312d1da8ea5e58ffe2bba1f3064fe5676c73a9fa"

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
