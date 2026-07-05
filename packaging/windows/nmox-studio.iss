; Inno Setup script for the NMOX Studio Windows installer.
; Compile from the repository root after `mvn package -DskipTests`:
;
;   iscc /DAppVersion=1.0.0 packaging\windows\nmox-studio.iss
;
; Produces application\target\dist\NMOX-Studio-<version>-windows-setup.exe.
; NMOX Studio requires a JDK 17+ on the machine (JAVA_HOME or PATH).

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
Name: "desktopicon"; Description: "Create a &desktop icon"; GroupDescription: "Additional icons:"

[Run]
Filename: "{app}\bin\nmoxstudio64.exe"; Description: "Launch NMOX Studio"; \
    Flags: nowait postinstall skipifsilent

[UninstallDelete]
; user/cache dirs live under %LOCALAPPDATA% and are left alone on purpose
Type: filesandordirs; Name: "{app}"
