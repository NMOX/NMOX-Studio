cask "nmox-studio" do
  version "1.180.0"
  sha256 "5c770f07571c40d3895050cb250e605a4e022eb359eefbb007c8724b5c16fc17"

  url "https://github.com/NMOX/NMOX-Studio/releases/download/v#{version}/NMOX-Studio-#{version}-macos.dmg",
      verified: "github.com/NMOX/NMOX-Studio/"
  name "NMOX Studio"
  desc "NetBeans RCP-based IDE for web development"
  homepage "https://github.com/NMOX/NMOX-Studio"

  depends_on macos: :big_sur

  app "NMOX Studio.app"

  zap trash: [
    "~/Library/Application Support/NMOXStudio",
    "~/Library/Caches/org.nmox.studio",
    "~/Library/Preferences/org.nmox.studio.plist",
  ]
end
