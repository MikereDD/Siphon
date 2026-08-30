[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+(?:\.\d+)*(?:-dev\.\d+(?:\.\d+)*)?$')]
    [string]$Version,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 2147483647)]
    [int]$VersionCode,

    [string]$MinimumVersion = "0.3-dev.5",

    [switch]$AllowStable,

    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
    throw "[Siphon Release] $Message"
}

function Run([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) {
        Fail "$Exe exited with code $LASTEXITCODE"
    }
}

function Get-FullCommit([string]$Ref = "HEAD") {
    $value = (git rev-parse $Ref).Trim()
    if ($LASTEXITCODE -ne 0 -or $value -notmatch '^[0-9a-f]{40}$') {
        Fail "Could not resolve $Ref to a full Git commit."
    }
    return $value
}

function Assert-CleanTree {
    $status = @(git status --porcelain)
    if ($LASTEXITCODE -ne 0) { Fail "git status failed." }
    if ($status.Count -ne 0) {
        Fail "Working tree is not clean. Commit/stash changes before publishing."
    }
}

function Normalize-Hex([string]$Value) {
    return ($Value -replace '[^0-9a-fA-F]', '').ToLowerInvariant()
}

function Get-LatestApkSigner {
    $sdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk\build-tools"
    if (-not (Test-Path $sdkRoot)) {
        Fail "Android SDK build-tools not found at $sdkRoot"
    }

    $tool = Get-ChildItem $sdkRoot -Filter "apksigner.bat" -Recurse -File |
        Sort-Object FullName -Descending |
        Select-Object -First 1 -ExpandProperty FullName

    if (-not $tool) { Fail "apksigner.bat was not found." }
    return $tool
}

function Get-OpenSsl {
    $gitOpenSsl = Join-Path $env:ProgramFiles "Git\usr\bin\openssl.exe"
    if (Test-Path $gitOpenSsl) { return $gitOpenSsl }

    $command = Get-Command openssl.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }

    Fail "OpenSSL was not found."
}

function Read-GradleVersion([string]$Path) {
    $text = Get-Content $Path -Raw
    $codeMatch = [regex]::Match($text, 'versionCode\s*=\s*(\d+)')
    $nameMatch = [regex]::Match($text, 'versionName\s*=\s*"([^"]+)"')

    if (-not $codeMatch.Success -or -not $nameMatch.Success) {
        Fail "Could not read versionCode/versionName from $Path"
    }

    return @{
        Code = [int]$codeMatch.Groups[1].Value
        Name = $nameMatch.Groups[1].Value
    }
}

function Set-GradleVersion([string]$Path, [int]$Code, [string]$Name) {
    $text = Get-Content $Path -Raw
    $text = [regex]::Replace($text, 'versionCode\s*=\s*\d+', "versionCode = $Code", 1)
    $text = [regex]::Replace($text, 'versionName\s*=\s*"[^"]+"', "versionName = `"$Name`"", 1)
    [IO.File]::WriteAllText((Resolve-Path $Path), $text.TrimEnd() + "`n")
}

function Get-Sha256([string]$Path) {
    return (Get-FileHash -Algorithm SHA256 $Path).Hash.ToLowerInvariant()
}

function Assert-PublishedAsset(
    [object[]]$Assets,
    [string]$Name,
    [string]$LocalPath
) {
    $asset = $Assets | Where-Object { $_.name -eq $Name } | Select-Object -First 1
    if (-not $asset) { Fail "Published release is missing $Name" }

    $local = Get-Item $LocalPath
    if ([int64]$asset.size -ne [int64]$local.Length) {
        Fail "Published size mismatch for $Name"
    }

    $expectedDigest = "sha256:$(Get-Sha256 $LocalPath)"
    if (-not $asset.digest -or $asset.digest.ToLowerInvariant() -ne $expectedDigest) {
        Fail "Published SHA-256 mismatch for $Name"
    }
}

function Confirm-Publish(
    [string]$ReleaseVersion,
    [string]$Channel,
    [string]$Commit,
    [string]$ApkHash
) {
    Write-Host ""
    Write-Host "All local validation gates passed."
    Write-Host ""
    Write-Host "About to publish:"
    Write-Host "  Siphon v$ReleaseVersion"
    Write-Host "  Channel: $Channel"
    Write-Host "  Commit: $Commit"
    Write-Host "  APK SHA-256: $ApkHash"
    Write-Host "  APK certificate: MATCH"
    Write-Host "  Detached signature: VERIFIED"
    Write-Host ""
    $answer = Read-Host "Publish release? [y/N]"
    return $answer -match '^(y|yes)$'
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $RepoRoot

try {
    if (-not (Test-Path ".git")) {
        Fail "Run this from the Siphon Git repository."
    }

    $branch = (git branch --show-current).Trim()
    if ($LASTEXITCODE -ne 0 -or $branch -ne "main") {
        Fail "Releases must be published from main."
    }

    $Channel = if ($Version -match '-dev\.') { "Development" } else { "Stable" }
    if ($Channel -eq "Stable" -and -not $AllowStable) {
        Fail "Stable publishing is blocked by default. Re-run with -AllowStable only after Stable is intentionally approved."
    }

    Assert-CleanTree

    foreach ($remote in @("origin", "github")) {
        git remote get-url $remote *> $null
        if ($LASTEXITCODE -ne 0) { Fail "Required Git remote '$remote' is missing." }
    }

    Run "git" @("fetch", "origin", "main", "--tags")
    Run "git" @("fetch", "github", "main", "--tags")

    $head = Get-FullCommit "HEAD"
    $originMain = Get-FullCommit "origin/main"
    $githubMain = Get-FullCommit "github/main"

    if (-not $DryRun -and ($head -ne $originMain -or $head -ne $githubMain)) {
        Fail "main is not synchronized across HEAD, origin/main, and github/main."
    }

    $Tag = "v$Version"
    if (-not $DryRun -and @(git tag --list $Tag).Count -gt 0) {
        Fail "Tag $Tag already exists."
    }

    $GradleFile = Join-Path $RepoRoot "app\build.gradle.kts"
    $current = Read-GradleVersion $GradleFile

    if ($DryRun) {
        if ($current.Name -ne $Version -or $current.Code -ne $VersionCode) {
            Fail "-DryRun never edits version metadata. Current build is $($current.Name) ($($current.Code)); requested $Version ($VersionCode)."
        }
    } else {
        Set-GradleVersion $GradleFile $VersionCode $Version

        Run "git" @("diff", "--check")
        Run "git" @("add", "app/build.gradle.kts")
        Run "git" @("diff", "--cached", "--check")

        $staged = @(git diff --cached --name-only)
        if ($staged.Count -ne 1 -or $staged[0] -ne "app/build.gradle.kts") {
            Fail "Version bump staging contains unexpected files."
        }

        Run "git" @("commit", "-m", "release: bump Siphon to $Version")
    }

    $BuildCommit = Get-FullCommit

    $createdPasswordEnv = $false
    if (-not $env:SIPHON_KEYSTORE_PASSWORD) {
        $secure = Read-Host "Siphon keystore password" -AsSecureString
        $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try {
            $env:SIPHON_KEYSTORE_PASSWORD =
                [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
            $createdPasswordEnv = $true
        }
        finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
        }
    }

    try {
        Run ".\gradlew.bat" @("testDebugUnitTest")
        Run ".\gradlew.bat" @("assembleDebug")
        Run ".\gradlew.bat" @("clean", "assembleRelease")
    }
    finally {
        if ($createdPasswordEnv) {
            Remove-Item Env:SIPHON_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
        }
    }

    $BuiltApk = Join-Path $RepoRoot "app\build\outputs\apk\release\app-arm64-v8a-release.apk"
    if (-not (Test-Path $BuiltApk)) {
        Fail "Release APK was not produced at $BuiltApk"
    }

    $ExpectedApkCert =
        "4f933b15ebef515aaa3e441579e768aaf314d5d4e27ec27fd1e94ecf9501513c"

    $ApkSigner = Get-LatestApkSigner
    $certOutput = & $ApkSigner verify --print-certs $BuiltApk 2>&1
    if ($LASTEXITCODE -ne 0) { Fail "apksigner verification failed." }

    $certLine = $certOutput |
        Where-Object { $_ -match 'certificate SHA-256 digest:' } |
        Select-Object -First 1

    if (-not $certLine) {
        Fail "Could not read APK signing certificate SHA-256."
    }

    $actualCert = Normalize-Hex (($certLine -split 'digest:')[1])
    if ($actualCert -ne $ExpectedApkCert) {
        Fail "APK signing certificate mismatch. Expected $ExpectedApkCert, got $actualCert"
    }

    $ReleaseFolderName = if ($DryRun) {
        "Siphon-v$Version-dryrun"
    } else {
        "Siphon-v$Version-release"
    }

    $ReleaseDir = Join-Path $HOME "Downloads\$ReleaseFolderName"
    New-Item -ItemType Directory -Path $ReleaseDir -Force | Out-Null

    $ReleaseApk = Join-Path $ReleaseDir "Siphon-v$Version.apk"
    $ReleaseSig = "$ReleaseApk.sig"
    Copy-Item $BuiltApk $ReleaseApk -Force

    & (Join-Path $PSScriptRoot "Generate-Checksums.ps1") -Path $ReleaseApk
    if ($LASTEXITCODE -ne 0) { Fail "Checksum generation failed." }

    $KeyRoot = Join-Path $HOME ".typezero\keys\Siphon"
    $PrivateKey = Join-Path $KeyRoot "siphon-release-signing-private.pem"
    $PublicKey = Join-Path $KeyRoot "siphon-release-signing-public.pem"

    if (-not (Test-Path $PrivateKey)) { Fail "Detached release private key not found." }
    if (-not (Test-Path $PublicKey)) { Fail "Detached release public key not found." }

    $OpenSsl = Get-OpenSsl
    $ReleaseKeyId = "typezero-siphon-release-01"
    $ReleasePublicKeySha256 =
        "3a08b42ab07b9d87ded218cb4df49d4a77c265f2a5f1a8a9847d5c0780c6546f"

    $tmpDer = Join-Path $env:TEMP "siphon-release-public-$PID.der"
    try {
        Run $OpenSsl @(
            "pkey",
            "-pubin",
            "-in", $PublicKey,
            "-outform", "DER",
            "-out", $tmpDer
        )
        $actualPublicKeyHash = Get-Sha256 $tmpDer
    }
    finally {
        Remove-Item $tmpDer -Force -ErrorAction SilentlyContinue
    }

    if ($actualPublicKeyHash -ne $ReleasePublicKeySha256) {
        Fail "Release public-key fingerprint mismatch. Expected $ReleasePublicKeySha256, got $actualPublicKeyHash"
    }

    Run $OpenSsl @("dgst", "-sha256", "-sign", $PrivateKey, "-out", $ReleaseSig, $ReleaseApk)
    Run $OpenSsl @("dgst", "-sha256", "-verify", $PublicKey, "-signature", $ReleaseSig, $ReleaseApk)

    $ApkHash = Get-Sha256 $ReleaseApk
    $SigHash = Get-Sha256 $ReleaseSig

    $PreviousTag = git describe --tags --abbrev=0 --match "v*" "$BuildCommit^" 2>$null
    $Changes = @()

    if ($LASTEXITCODE -eq 0 -and $PreviousTag) {
        $Changes = @(git log "$PreviousTag..$BuildCommit^" --pretty=format:"- %s")
    }

    if ($Changes.Count -eq 0) {
        $Changes = @("- Release maintenance and validation.")
    }

    $ReleaseNotes = Join-Path $ReleaseDir "release-notes.md"
    $notes = @(
        "# Siphon v$Version",
        "",
        "$Channel release.",
        "",
        "## Changes",
        ""
    ) + $Changes + @(
        "",
        "## Source",
        "",
        "Commit: $BuildCommit"
    )
    $notes -join "`n" | Set-Content -Encoding utf8 $ReleaseNotes

    $Validation = Join-Path $ReleaseDir "release-validation.md"
@"
# Siphon v$Version Release Validation

Source commit: $BuildCommit

APK:
- File: $(Split-Path $ReleaseApk -Leaf)
- Size: $((Get-Item $ReleaseApk).Length) bytes
- SHA-256: $ApkHash

APK signing certificate SHA-256:
$ExpectedApkCert

Detached signature:
- File: $(Split-Path $ReleaseSig -Leaf)
- Size: $((Get-Item $ReleaseSig).Length) bytes
- SHA-256: $SigHash
- Verification: Verified OK

Release key:
- Key ID: $ReleaseKeyId
- Public-key SHA-256: $ReleasePublicKeySha256
"@ | Set-Content -Encoding utf8 $Validation

    $ReleaseManifest = Join-Path $ReleaseDir "release-manifest.json"
    & (Join-Path $PSScriptRoot "Generate-SiphonReleaseManifest.ps1") `
        -Version $Version `
        -ApkPath $ReleaseApk `
        -SignaturePath $ReleaseSig `
        -ApkSigningCertificateSha256 $ExpectedApkCert `
        -ReleaseKeyId $ReleaseKeyId `
        -ReleasePublicKeySha256 $ReleasePublicKeySha256 `
        -Commit $BuildCommit `
        -MinimumVersion $MinimumVersion `
        -Output $ReleaseManifest

    if ($LASTEXITCODE -ne 0) { Fail "Manifest generation failed." }

    $Manifest = Get-Content $ReleaseManifest -Raw | ConvertFrom-Json
    $ExpectedWireChannel = if ($Channel -eq "Development") { "development" } else { "stable" }

    if ($Manifest.version -ne $Version) { Fail "Generated manifest version mismatch." }
    if ($Manifest.channel -ne $ExpectedWireChannel) { Fail "Generated manifest channel mismatch." }
    if ($Manifest.source.commit -ne $BuildCommit) { Fail "Generated manifest commit mismatch." }
    if ($Manifest.source.tag -ne $Tag) { Fail "Generated manifest tag mismatch." }
    if ($Manifest.assets.Count -ne 1) { Fail "Generated manifest must contain exactly one APK asset." }
    if ($Manifest.assets[0].fileName -ne "Siphon-v$Version.apk") { Fail "Generated manifest APK filename mismatch." }
    if ($Manifest.assets[0].size -ne (Get-Item $ReleaseApk).Length) { Fail "Generated manifest APK size mismatch." }
    if ($Manifest.assets[0].sha256 -ne $ApkHash) { Fail "Generated manifest APK hash mismatch." }
    if ($Manifest.assets[0].signingCertificateSha256 -ne $ExpectedApkCert) { Fail "Generated manifest APK certificate mismatch." }
    if ($Manifest.assets[0].signature.fileName -ne "Siphon-v$Version.apk.sig") { Fail "Generated manifest signature filename mismatch." }
    if ($Manifest.assets[0].signature.size -ne (Get-Item $ReleaseSig).Length) { Fail "Generated manifest signature size mismatch." }
    if ($Manifest.assets[0].signature.sha256 -ne $SigHash) { Fail "Generated manifest signature hash mismatch." }
    if ($Manifest.assets[0].signature.keyId -ne $ReleaseKeyId) { Fail "Generated manifest release-key ID mismatch." }
    if ($Manifest.assets[0].signature.publicKeySha256 -ne $ReleasePublicKeySha256) { Fail "Generated manifest release-key fingerprint mismatch." }

    Write-Host ""
    Write-Host "Local release validation PASS"
    Write-Host "  Version: $Version ($VersionCode)"
    Write-Host "  Channel: $Channel"
    Write-Host "  Commit: $BuildCommit"
    Write-Host "  APK SHA-256: $ApkHash"
    Write-Host "  APK cert: MATCH"
    Write-Host "  Release key: MATCH"
    Write-Host "  Detached signature: VERIFIED"
    Write-Host "  Artifacts: $ReleaseDir"

    if ($DryRun) {
        Write-Host ""
        Write-Host "Dry run complete. No Git tag, push, GitHub release, or live manifest was published."
        exit 0
    }

    if (-not (Confirm-Publish $Version $Channel $BuildCommit $ApkHash)) {
        Write-Host "Publication cancelled. Local build commit/artifacts remain available."
        exit 0
    }

    Run "gh" @("auth", "status")

    # The tag points to the exact commit that produced the APK.
    Run "git" @("tag", "-a", $Tag, $BuildCommit, "-m", "Siphon v$Version")

    Run "git" @("push", "origin", "main")
    Run "git" @("push", "github", "main")
    Run "git" @("push", "origin", $Tag)
    Run "git" @("push", "github", $Tag)

    $ReleaseArguments = @(
        "release", "create", $Tag,
        $ReleaseApk,
        "$ReleaseApk.sha256",
        $ReleaseSig,
        $ReleaseManifest,
        $ReleaseNotes,
        $Validation,
        "--repo", "MikereDD/Siphon",
        "--title", "Siphon v$Version",
        "--notes-file", $ReleaseNotes,
        "--verify-tag"
    )

    if ($Channel -eq "Development") {
        $ReleaseArguments += "--prerelease"
    }

    Run "gh" $ReleaseArguments

    # The live updater manifest is published only after all release assets exist.
    Copy-Item $ReleaseManifest (Join-Path $RepoRoot "release-manifest.json") -Force
    Run "git" @("add", "release-manifest.json")
    Run "git" @("diff", "--cached", "--check")

    $manifestStaged = @(git diff --cached --name-only)
    if ($manifestStaged.Count -ne 1 -or $manifestStaged[0] -ne "release-manifest.json") {
        Fail "Live-manifest staging contains unexpected files."
    }

    Run "git" @("commit", "-m", "release: publish Siphon $Version manifest")
    Run "git" @("push", "origin", "main")
    Run "git" @("push", "github", "main")

    $publishedJson = gh release view $Tag `
        --repo MikereDD/Siphon `
        --json tagName,isPrerelease,assets,url

    if ($LASTEXITCODE -ne 0) { Fail "Could not verify the published GitHub release." }
    $published = $publishedJson | ConvertFrom-Json

    if ($published.tagName -ne $Tag) { Fail "Published release tag mismatch." }

    $expectedPrerelease = ($Channel -eq "Development")
    if ([bool]$published.isPrerelease -ne $expectedPrerelease) {
        Fail "Published prerelease/stable classification mismatch."
    }

    $Artifacts = [ordered]@{
        "Siphon-v$Version.apk" = $ReleaseApk
        "Siphon-v$Version.apk.sha256" = "$ReleaseApk.sha256"
        "Siphon-v$Version.apk.sig" = $ReleaseSig
        "release-manifest.json" = $ReleaseManifest
        "release-notes.md" = $ReleaseNotes
        "release-validation.md" = $Validation
    }

    foreach ($entry in $Artifacts.GetEnumerator()) {
        Assert-PublishedAsset $published.assets $entry.Key $entry.Value
    }

    # Confirm the manifest that main exposes is the exact release we just published.
    $encoded = gh api `
        --method GET `
        "repos/MikereDD/Siphon/contents/release-manifest.json" `
        -f ref=main `
        --jq .content

    if ($LASTEXITCODE -ne 0 -or -not $encoded) {
        Fail "Could not fetch the live manifest from GitHub main."
    }

    $liveText = [Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String(($encoded -replace '\s', ''))
    )
    $live = $liveText | ConvertFrom-Json

    if ($live.version -ne $Version -or
        $live.source.commit -ne $BuildCommit -or
        $live.assets[0].sha256 -ne $ApkHash -or
        $live.assets[0].signature.sha256 -ne $SigHash) {
        Fail "GitHub main does not expose the expected live release manifest."
    }

    Write-Host ""
    Write-Host "=============================================="
    Write-Host " Siphon v$Version RELEASE PASS"
    Write-Host "=============================================="
    Write-Host "Channel: $Channel"
    Write-Host "Source:  $BuildCommit"
    Write-Host "APK:     $ApkHash"
    Write-Host "Release: $($published.url)"
    Write-Host "Assets:  verified by published SHA-256 digest"
    Write-Host "Manifest: verified live on GitHub main"
    Write-Host ""
}
finally {
    Pop-Location
}
