param(
    [Parameter(Mandatory=$true)][string]$Version,
    [Parameter(Mandatory=$true)][string]$ApkPath,
    [Parameter(Mandatory=$true)][string]$SignaturePath,
    [Parameter(Mandatory=$true)][string]$ApkSigningCertificateSha256,
    [Parameter(Mandatory=$true)][string]$ReleaseKeyId,
    [Parameter(Mandatory=$true)][string]$ReleasePublicKeySha256,
    [Parameter(Mandatory=$true)][string]$Commit,
    [string]$MinimumVersion = "0.3-dev.5",
    [string]$Output = "release-manifest.json"
)
$ErrorActionPreference = "Stop"

if ($Version -notmatch '^\d+(?:\.\d+)*(?:-dev\.\d+(?:\.\d+)*)?$') {
    throw "Version does not match the Typezero version grammar: $Version"
}
foreach ($Hash in @($ApkSigningCertificateSha256,$ReleasePublicKeySha256)) {
    if ($Hash -notmatch '^[0-9a-fA-F]{64}$') { throw "Expected a SHA-256 hex digest." }
}
if ($Commit -notmatch '^[0-9a-fA-F]{40}$') { throw "Commit must be a full 40-character Git SHA." }

$Apk = Get-Item (Resolve-Path $ApkPath)
$Sig = Get-Item (Resolve-Path $SignaturePath)
$ExpectedApk = "Siphon-v$Version.apk"
$ExpectedSig = "$ExpectedApk.sig"
if ($Apk.Name -ne $ExpectedApk) { throw "APK must be named $ExpectedApk" }
if ($Sig.Name -ne $ExpectedSig) { throw "Signature must be named $ExpectedSig" }

$Channel = if ($Version -match '-dev\.') { "development" } else { "stable" }
$Tag = "v$Version"
$Base = "https://github.com/MikereDD/Siphon/releases/download/$Tag"

$Manifest = [ordered]@{
    schemaVersion = 2
    appId = "siphon"
    displayName = "Siphon"
    platform = "android"
    architecture = "arm64-v8a"
    channel = $Channel
    version = $Version
    publishedAt = [DateTime]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
    minimumVersion = $MinimumVersion
    updaterProtocolVersion = 2
    minimumUpdaterProtocolVersion = 2
    mandatory = $false
    releaseNotesUrl = "https://github.com/MikereDD/Siphon/releases/tag/$Tag"
    changelogUrl = "CHANGELOG.md"
    assets = @(
        [ordered]@{
            fileName = $Apk.Name
            downloadUrl = "$Base/$($Apk.Name)"
            size = $Apk.Length
            sha256 = (Get-FileHash -Algorithm SHA256 $Apk.FullName).Hash.ToLowerInvariant()
            signature = [ordered]@{
                algorithm = "rsa-sha256"
                fileName = $Sig.Name
                downloadUrl = "$Base/$($Sig.Name)"
                size = $Sig.Length
                sha256 = (Get-FileHash -Algorithm SHA256 $Sig.FullName).Hash.ToLowerInvariant()
                keyId = $ReleaseKeyId
                publicKeySha256 = $ReleasePublicKeySha256.ToLowerInvariant()
            }
            packageId = "com.typezero.siphon"
            signingCertificateSha256 = $ApkSigningCertificateSha256.ToLowerInvariant()
        }
    )
    source = [ordered]@{
        repositoryUrl = "https://github.com/MikereDD/Siphon"
        tag = $Tag
        commit = $Commit.ToLowerInvariant()
    }
    rollback = [ordered]@{
        supported = $false
        retainVersions = 0
    }
}

$Json = $Manifest | ConvertTo-Json -Depth 10
$Json | Set-Content -Encoding utf8 $Output
Write-Host "Wrote $Output"
