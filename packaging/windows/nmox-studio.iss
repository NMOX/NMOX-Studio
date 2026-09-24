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

; [CustomMessages] below carries Cyrillic, Hebrew and Arabic in a UTF-8 file
; with no BOM, which Inno Setup reads as UTF-8 only from 6.3 on. An older
; compiler would read it as ANSI and ship mojibake, so it refuses instead.
#if Ver < EncodeVer(6,3,0)
  #error Inno Setup 6.3 or newer is required (UTF-8 .iss without a BOM)
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
; The "nmox" PATH task edits the environment; tell Explorer so a NEW
; terminal sees it without a sign-out (terminals already open keep theirs).
ChangesEnvironment=yes

[Languages]
; The wizard speaks the languages the IDE speaks, using Inno Setup's OWN
; translations rather than any written here: an installer is the first text
; a user ever sees, and it is the one surface where a wrong word cannot be
; corrected by a later release. Inno picks the entry matching the machine's
; language and falls back to English (v2.105.0). Three sentences are ours,
; the "Add nmox to PATH" task and the "Open with NMOX Studio" folder task
; with its Explorer label (3.1.0): Inno ships no message for them, so they
; live in [CustomMessages] below, written to docs/i18n/conventions.md.
;
; Five of the IDE's fifteen are absent ON PURPOSE — Bahasa Indonesia,
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
Name: "he"; MessagesFile: "compiler:Languages\Hebrew.isl"
Name: "ar"; MessagesFile: "compiler:Languages\Arabic.isl"

[Files]
Source: "..\..\application\target\nmoxstudio\*"; DestDir: "{app}"; \
    Flags: recursesubdirs createallsubdirs ignoreversion
; Shipped so shortcuts stay branded even where the exe's embedded icon
; is the stock launcher one (e.g. installers built outside CI's rcedit step).
Source: "..\icons\nmox-studio.ico"; DestDir: "{app}"
; `nmox .` from a terminal, like `code .`. A folder of its own, so PATH gains
; exactly one command and not the launcher exes beside it in bin\. Shipped
; whether or not the PATH task is ticked, so it can be added by hand later.
Source: "nmox.cmd"; DestDir: "{app}\cli"

[Icons]
Name: "{group}\NMOX Studio"; Filename: "{app}\bin\nmoxstudio64.exe"; \
    IconFilename: "{app}\nmox-studio.ico"
Name: "{autodesktop}\NMOX Studio"; Filename: "{app}\bin\nmoxstudio64.exe"; \
    IconFilename: "{app}\nmox-studio.ico"; Tasks: desktopicon

[Tasks]
; {cm:...} resolves from the chosen language's own message file, so these
; three lines are translated everywhere without a word being written here
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
; Ticked by default, as VS Code's installer ticks "Add to PATH": a developer
; installing an IDE expects its command. Inno ships no message for this, so
; this one sentence is ours, in [CustomMessages] for every language above
; (InstallerLanguagesTest holds the coverage).
Name: "addtopath"; Description: "{cm:AddToPath}"
; "Open with NMOX Studio" on a folder's right-click menu in Explorer, and on
; the empty space inside an open folder (3.1.0). Unticked by default, as VS
; Code's installer leaves its "Open with Code" folder task unticked: it adds
; a line to a menu every folder on the machine shares, and that is the
; user's call to make. The verb is --aim, File > Open Folder...'s own, so a
; folder with or without a manifest is aimed rather than shown as a raw tab.
Name: "contextmenu"; Description: "{cm:AddFolderContextMenu}"; Flags: unchecked

[CustomMessages]
AddToPath=Add "nmox" to PATH (open a folder from a terminal with "nmox .")
es.AddToPath=Añadir «nmox» al PATH (abre una carpeta desde una terminal con «nmox .»)
fr.AddToPath=Ajouter « nmox » au PATH (ouvrez un dossier depuis un terminal avec « nmox . »)
de.AddToPath=„nmox“ zum PATH hinzufügen (öffnen Sie einen Ordner im Terminal mit „nmox .“)
ru.AddToPath=Добавить «nmox» в PATH (открывайте папку из терминала командой «nmox .»)
uk.AddToPath=Додати «nmox» до PATH (відкривайте теку з термінала командою «nmox .»)
pl.AddToPath=Dodaj „nmox” do PATH (otwieraj katalog z terminala poleceniem „nmox .”)
pt.AddToPath=Adicionar “nmox” ao PATH (abra uma pasta pelo terminal com “nmox .”)
he.AddToPath=הוספת "nmox" ל-PATH (פתחו תיקייה מהמסוף עם "nmox .‎")
ar.AddToPath=إضافة «nmox» لـ PATH (افتحوا مجلد من الترمينال بـ «nmox .‎»)

AddFolderContextMenu=Add "Open with NMOX Studio" to the right-click menu of folders in Explorer
es.AddFolderContextMenu=Añadir «Abrir con NMOX Studio» al menú contextual de las carpetas en el Explorador
fr.AddFolderContextMenu=Ajouter « Ouvrir avec NMOX Studio » au menu contextuel des dossiers dans l’Explorateur
de.AddFolderContextMenu=„Mit NMOX Studio öffnen“ zum Kontextmenü von Ordnern im Explorer hinzufügen
ru.AddFolderContextMenu=Добавить «Открыть в NMOX Studio» в контекстное меню папок в Проводнике
uk.AddFolderContextMenu=Додати «Відкрити в NMOX Studio» до контекстного меню тек у Провіднику
pl.AddFolderContextMenu=Dodaj „Otwórz w NMOX Studio” do menu kontekstowego katalogów w Eksploratorze
pt.AddFolderContextMenu=Adicionar “Abrir com o NMOX Studio” ao menu de contexto das pastas no Explorador
he.AddFolderContextMenu=הוספת "פתיחה באמצעות NMOX Studio" לתפריט ההקשר של תיקיות בסייר הקבצים
ar.AddFolderContextMenu=إضافة «فتح بـ NMOX Studio» لقايمة كليك يمين بتاعة المجلدات في مستكشف الملفات
OpenWithNmox=Open with NMOX Studio
es.OpenWithNmox=Abrir con NMOX Studio
fr.OpenWithNmox=Ouvrir avec NMOX Studio
de.OpenWithNmox=Mit NMOX Studio öffnen
ru.OpenWithNmox=Открыть в NMOX Studio
uk.OpenWithNmox=Відкрити в NMOX Studio
pl.OpenWithNmox=Otwórz w NMOX Studio
pt.OpenWithNmox=Abrir com o NMOX Studio
he.OpenWithNmox=פתיחה באמצעות NMOX Studio
ar.OpenWithNmox=فتح بـ NMOX Studio

[Registry]
; Append {app}\cli to the Path of whoever the install is for: the user's own
; environment for a per-user install, the machine's for an all-users one.
; Each Check skips its entry when the folder is already there, so a
; reinstall never grows the Path. [Code] takes it out again on uninstall.
Root: HKCU; Subkey: "Environment"; ValueType: expandsz; ValueName: "Path"; \
    ValueData: "{olddata};{app}\cli"; Tasks: addtopath; Check: NeedsUserPathEntry
Root: HKLM; Subkey: "SYSTEM\CurrentControlSet\Control\Session Manager\Environment"; \
    ValueType: expandsz; ValueName: "Path"; ValueData: "{olddata};{app}\cli"; \
    Tasks: addtopath; Check: NeedsSystemPathEntry
; The folder task: Explorer's right-click menu on a folder (Directory) and
; on the empty space inside an open one (Directory\Background), for whoever
; the install is for - HKA is HKCU for a per-user install and HKLM for an
; all-users one. The label comes from the chosen language ({cm:OpenWithNmox})
; and uninsdeletekey takes each key, command and all, out on uninstall.
; %V is the folder; "\." after it keeps a drive root ("C:\") from ending in
; \" - an argument parser reads \" as an escaped quote, and C:\. is still
; C:\ once --aim normalizes it. Windows 11 lists these under "Show more
; options". The windows-installer-check workflow installs, reads the keys
; and uninstalls for real.
Root: HKA; Subkey: "Software\Classes\Directory\shell\NMOXStudio"; ValueType: string; ValueName: ""; \
    ValueData: "{cm:OpenWithNmox}"; Tasks: contextmenu; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Directory\shell\NMOXStudio"; ValueType: string; ValueName: "Icon"; \
    ValueData: "{app}\nmox-studio.ico"; Tasks: contextmenu
Root: HKA; Subkey: "Software\Classes\Directory\shell\NMOXStudio\command"; ValueType: string; ValueName: ""; \
    ValueData: """{app}\bin\nmoxstudio64.exe"" --aim ""%V\."""; Tasks: contextmenu
Root: HKA; Subkey: "Software\Classes\Directory\Background\shell\NMOXStudio"; ValueType: string; ValueName: ""; \
    ValueData: "{cm:OpenWithNmox}"; Tasks: contextmenu; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Directory\Background\shell\NMOXStudio"; ValueType: string; ValueName: "Icon"; \
    ValueData: "{app}\nmox-studio.ico"; Tasks: contextmenu
Root: HKA; Subkey: "Software\Classes\Directory\Background\shell\NMOXStudio\command"; ValueType: string; ValueName: ""; \
    ValueData: """{app}\bin\nmoxstudio64.exe"" --aim ""%V\."""; Tasks: contextmenu

[Run]
Filename: "{app}\bin\nmoxstudio64.exe"; Description: "{cm:LaunchProgram,NMOX Studio}"; \
    Flags: nowait postinstall skipifsilent

[UninstallDelete]
; user/cache dirs live under %LOCALAPPDATA% and are left alone on purpose
Type: filesandordirs; Name: "{app}"

[Code]
const
  UserEnvKey = 'Environment';
  SystemEnvKey = 'SYSTEM\CurrentControlSet\Control\Session Manager\Environment';

function CliDir(): String;
begin
  Result := ExpandConstant('{app}\cli');
end;

{ True when Dir is one of the ;-separated entries of the Path under
  Root\SubKey, case-insensitively, with or without a trailing backslash. }
function PathHasEntry(Root: Integer; SubKey, Dir: String): Boolean;
var
  Paths: String;
begin
  Result := False;
  if RegQueryStringValue(Root, SubKey, 'Path', Paths) then
    Result := (Pos(';' + Uppercase(Dir) + ';', ';' + Uppercase(Paths) + ';') > 0)
      or (Pos(';' + Uppercase(Dir) + '\;', ';' + Uppercase(Paths) + ';') > 0);
end;

function NeedsUserPathEntry(): Boolean;
begin
  Result := (not IsAdminInstallMode) and (not PathHasEntry(HKEY_CURRENT_USER, UserEnvKey, CliDir()));
end;

function NeedsSystemPathEntry(): Boolean;
begin
  Result := IsAdminInstallMode and (not PathHasEntry(HKEY_LOCAL_MACHINE, SystemEnvKey, CliDir()));
end;

{ Rewrites the Path without Dir, and only when Dir was in it: every other
  entry, and the value itself when Dir is absent, is left as it was found. }
procedure RemovePathEntry(Root: Integer; SubKey, Dir: String);
var
  Paths, Rest, Part, Kept: String;
  P: Integer;
  Found: Boolean;
begin
  if not RegQueryStringValue(Root, SubKey, 'Path', Paths) then
    exit;
  Rest := Paths + ';';
  Kept := '';
  Found := False;
  while Rest <> '' do
  begin
    P := Pos(';', Rest);
    Part := Copy(Rest, 1, P - 1);
    Delete(Rest, 1, P);
    if CompareText(RemoveBackslashUnlessRoot(Part), RemoveBackslashUnlessRoot(Dir)) = 0 then
      Found := True
    else if Part <> '' then
    begin
      if Kept <> '' then
        Kept := Kept + ';';
      Kept := Kept + Part;
    end;
  end;
  if Found then
    RegWriteExpandStringValue(Root, SubKey, 'Path', Kept);
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usUninstall then
  begin
    if IsAdminInstallMode then
      RemovePathEntry(HKEY_LOCAL_MACHINE, SystemEnvKey, CliDir())
    else
      RemovePathEntry(HKEY_CURRENT_USER, UserEnvKey, CliDir());
  end;
end;
