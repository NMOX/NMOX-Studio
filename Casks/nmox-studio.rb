cask "nmox-studio" do
  version "1.167.0"
  sha256 "f957a20617378d1de0da8936c95d70976379e16f88355d6f02176b5ba6f86620"

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
