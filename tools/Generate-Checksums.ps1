param(
    [Parameter(Mandatory=$true)][string[]]$Path
)
$ErrorActionPreference = "Stop"
foreach ($Item in $Path) {
    $Resolved = (Resolve-Path $Item).Path
    $Hash = (Get-FileHash -Algorithm SHA256 $Resolved).Hash.ToLowerInvariant()
    "$Hash  $([IO.Path]::GetFileName($Resolved))" | Set-Content -NoNewline -Encoding ascii "$Resolved.sha256"
    Write-Host "$Hash  $Resolved"
}
