<#
.SYNOPSIS
    Authenticode-signs the files it is given, or does nothing at all.

.DESCRIPTION
    ONE home for the signtool invocation, called twice by the release
    workflow: once for the launcher .exe files (before Inno Setup packages
    them) and once for the installer it produces. Signing the launchers
    after the installer would be pointless, and signing them before rcedit
    rewrites their icons would invalidate the signature, so the order in
    release.yml is: brand -> sign launchers -> build installer -> sign
    installer.

    WITHOUT CREDENTIALS THIS SCRIPT IS A NO-OP. It prints a ::notice:: and
    exits 0, so a release without the secrets produces byte-identical
    artifacts to one built before this script existed.

    Two credential shapes, chosen by which secrets are present:

      Azure Trusted Signing (AZURE_TRUSTED_SIGNING_ENDPOINT + friends)
        The route ledger 86 names, ~$10/month. Microsoft's dlib plugs into
        signtool and the private key never leaves Azure. THIS IS THE ONE TO
        USE: since the CA/Browser Forum's June 2023 rules, a publicly
        trusted code-signing key must live on certified hardware or in a
        cloud HSM, so a plain exportable .pfx from a public CA is no longer
        something you can buy.

      A .pfx file (WINDOWS_SIGNING_PFX_BASE64 + password)
        Kept because it is the shape an existing pre-2023 certificate, an
        internal CA, or a self-signed test certificate arrives in, and
        because it is the half that can be written and reviewed without an
        Azure subscription to test against. A self-signed .pfx does NOT
        remove SmartScreen warnings; it only proves the pipeline works.

    NEITHER PATH HAS EVER RUN. There is no Windows code-signing account
    (ledger 86 - a purchase, not an engineering decision). Both are written
    from Microsoft's documentation and reviewed; the first release carrying
    the secrets is their first test. docs/engineering/release-signing.md
    says what to watch.
#>
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Path)

$ErrorActionPreference = 'Stop'

if (-not $Path -or $Path.Count -eq 0) {
    Write-Error "usage: authenticode-sign.ps1 <file> [file ...]"
    exit 2
}

$azure = $env:AZURE_TRUSTED_SIGNING_ENDPOINT
$pfxB64 = $env:WINDOWS_SIGNING_PFX_BASE64

if (-not $azure -and -not $pfxB64) {
    Write-Host "::notice::Windows Authenticode signing skipped (no signing secrets set); binaries ship unsigned, as before."
    exit 0
}

# signtool.exe is not on PATH on the hosted runners; it lives in the
# Windows SDK. Take the highest SDK version present rather than pinning one,
# because the image's SDK moves and a pinned path would break silently.
$signtool = Get-ChildItem "C:\Program Files (x86)\Windows Kits\10\bin\*\x64\signtool.exe" -ErrorAction SilentlyContinue |
    Sort-Object { [version]($_.Directory.Parent.Name) } -Descending |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $signtool) { throw "signtool.exe not found in the Windows 10 SDK" }
Write-Host "signtool: $signtool"

if ($azure) {
    # The dlib reads AZURE_CLIENT_ID / AZURE_TENANT_ID / AZURE_CLIENT_SECRET
    # from the environment through DefaultAzureCredential - they are never
    # passed on the command line.
    $clientVersion = '1.0.60'
    $nupkg = "$env:RUNNER_TEMP\trusted-signing-client.zip"
    $clientDir = "$env:RUNNER_TEMP\trusted-signing-client"
    if (-not (Test-Path $clientDir)) {
        Invoke-WebRequest -Uri "https://www.nuget.org/api/v2/package/Microsoft.Trusted.Signing.Client/$clientVersion" -OutFile $nupkg
        Expand-Archive $nupkg -DestinationPath $clientDir
    }
    $dlib = Join-Path $clientDir 'bin\x64\Azure.CodeSigning.Dlib.dll'
    if (-not (Test-Path $dlib)) { throw "Trusted Signing dlib not found at $dlib" }
    $metadata = "$env:RUNNER_TEMP\trusted-signing-metadata.json"
    @{
        Endpoint               = $azure
        CodeSigningAccountName = $env:AZURE_TRUSTED_SIGNING_ACCOUNT
        CertificateProfileName = $env:AZURE_TRUSTED_SIGNING_PROFILE
    } | ConvertTo-Json | Set-Content -Path $metadata -Encoding utf8
    # Trusted Signing certificates are short-lived by design, so the
    # timestamp is not optional: without it every signature expires in days.
    $signArgs = @('/v', '/fd', 'SHA256', '/tr', 'http://timestamp.acs.microsoft.com', '/td', 'SHA256',
                  '/dlib', $dlib, '/dmdf', $metadata)
    Write-Host "::notice::Windows Authenticode signing enabled (Azure Trusted Signing)."
} else {
    $pfx = "$env:RUNNER_TEMP\nmox-authenticode.pfx"
    if (-not (Test-Path $pfx)) {
        [IO.File]::WriteAllBytes($pfx, [Convert]::FromBase64String($pfxB64))
    }
    $signArgs = @('/v', '/fd', 'SHA256', '/tr', 'http://timestamp.digicert.com', '/td', 'SHA256',
                  '/f', $pfx)
    if ($env:WINDOWS_SIGNING_PFX_PASSWORD) { $signArgs += @('/p', $env:WINDOWS_SIGNING_PFX_PASSWORD) }
    Write-Host "::notice::Windows Authenticode signing enabled (PFX certificate)."
}

foreach ($file in $Path) {
    if (-not (Test-Path $file)) { throw "nothing to sign at $file" }
    & $signtool sign @signArgs $file
    if ($LASTEXITCODE -ne 0) { throw "signtool sign failed on $file (exit $LASTEXITCODE)" }
    # /pa: the Authenticode policy, i.e. the check Windows itself makes.
    # Verifying is what turns "the command ran" into "the file is signed".
    & $signtool verify /pa /v $file
    if ($LASTEXITCODE -ne 0) { throw "signtool verify failed on $file (exit $LASTEXITCODE)" }
    Write-Host "signed $file"
}
