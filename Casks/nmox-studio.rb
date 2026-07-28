cask "nmox-studio" do
  version "1.195.1"
  sha256 "69fbda6b1cb74ace276942a73bc00bd3dd28baf8cda640269c200f1f28cd0e71"

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
