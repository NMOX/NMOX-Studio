cask "nmox-studio" do
  version "1.112.0"
  sha256 "3726f6ce03376e21d167a7c0be8521af1cc1c2dc74b074374836cd368a13f387"

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
