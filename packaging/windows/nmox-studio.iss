; Inno Setup script for the NMOX Studio Windows installer.
; Compile from the repository root after `mvn package -DskipTests`:
;
;   iscc /DAppVersion=1.0.0 packaging\windows\nmox-studio.iss
;
; Produces application\target\dist\NMOX-Studio-<version>-windows-setup.exe.
; The installer carries its own Java runtime — nothing needs to be installed
; first. Only the portable zip uses the machine's own Java (21+).

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif

[Setup]
AppId={{8B1B5E1E-9C5A-4E0B-9A43-NMOXSTUDIO01}
AppName=NMOX Studio
AppVersion={#AppVersion}
AppPublisher=NMOX
AppPublisherURL=https://github.com/NMOX/NMOX-Studio
DefaultDirName={autopf}\NMOX Studio
DefaultGroupName=NMOX Studio
DisableProgramGroupPage=yes
LicenseFile=..\..\LICENSE
OutputDir=..\..\application\target\dist
OutputBaseFilename=NMOX-Studio-{#AppVersion}-windows-setup
Compression=lzma2
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequiredOverridesAllowed=dialog
UninstallDisplayName=NMOX Studio
; Brand the setup exe itself (and Add/Remove Programs) with the NMOX icon.
SetupIconFile=..\icons\nmox-studio.ico
UninstallDisplayIcon={app}\nmox-studio.ico

[Languages]
; The wizard speaks the languages the IDE speaks, using Inno Setup's OWN
; translations rather than any written here: an installer is the first text
; a user ever sees, and it is the one surface where a wrong word cannot be
; corrected by a later release. Inno picks the entry matching the machine's
; language and falls back to English (v2.105.0).
;
; Five of the IDE's thirteen are absent ON PURPOSE — Bahasa Indonesia,
; Filipino, Tiếng Việt, 简体中文 and हिन्दी ship no official .isl with Inno
; Setup, and vendoring an unreviewed community file would put text nobody
; here can read in front of every user of those languages. English is the
; honest answer until an official translation exists.
Name: "en"; MessagesFile: "compiler:Default.isl"
Name: "es"; MessagesFile: "compiler:Languages\Spanish.isl"
Name: "fr"; MessagesFile: "compiler:Languages\French.isl"
Name: "de"; MessagesFile: "compiler:Languages\German.isl"
Name: "ru"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "uk"; MessagesFile: "compiler:Languages\Ukrainian.isl"
Name: "pl"; MessagesFile: "compiler:Languages\Polish.isl"
Name: "pt"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"

[Files]
Source: "..\..\application\target\nmoxstudio\*"; DestDir: "{app}"; \
    Flags: recursesubdirs createallsubdirs ignoreversion
; Shipped so shortcuts stay branded even where the exe's embedded icon
; is the stock launcher one (e.g. installers built outside CI's rcedit step).
Source: "..\icons\nmox-studio.ico"; DestDir: "{app}"

[Icons]
Name: "{group}\NMOX Studio"; Filename: "{app}\bin\nmoxstudio64.exe"; \
    IconFilename: "{app}\nmox-studio.ico"
Name: "{autodesktop}\NMOX Studio"; Filename: "{app}\bin\nmoxstudio64.exe"; \
    IconFilename: "{app}\nmox-studio.ico"; Tasks: desktopicon

[Tasks]
; {cm:...} resolves from the chosen language's own message file, so these
; three lines are translated everywhere without a word being written here
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Run]
Filename: "{app}\bin\nmoxstudio64.exe"; Description: "{cm:LaunchProgram,NMOX Studio}"; \
    Flags: nowait postinstall skipifsilent

[UninstallDelete]
; user/cache dirs live under %LOCALAPPDATA% and are left alone on purpose
Type: filesandordirs; Name: "{app}"
