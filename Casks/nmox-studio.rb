cask "nmox-studio" do
  version "3.1.1"
  sha256 "7650d280d04c73d9c7a3ff2d769c6603ccd8ea626159e5b4f26b7d6e466984a5"

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
