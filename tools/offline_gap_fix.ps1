param(
    [Parameter(Mandatory=$true, ValueFromRemainingArguments=$true)]
    [string[]]$Coords
)

# Copies artifacts (group:artifact:version) from the Gradle cache into the local Maven repo
# (~/.m2), reconstructing the standard Maven layout. This is used to satisfy Gradle's offline
# resolution when the Gradle module cache is incomplete or its metadata is not usable offline.

$gradleBase = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"
$m2 = "$env:USERPROFILE\.m2\repository"

function Convert-GroupPath([string]$g) {
    return $g.Replace('.', '\')
}

foreach ($coordRaw in $Coords) {
    $coord = $coordRaw.Trim()
    if (-not $coord) { continue }
    $parts = $coord -split ':'
    if ($parts.Count -ne 3) {
        Write-Warning "Skipping malformed coord: $coord"
        continue
    }
    $group = $parts[0]
    $artifact = $parts[1]
    $version = $parts[2]
    $srcDir = Join-Path $gradleBase "$group\$artifact\$version"
    if (-not (Test-Path $srcDir)) {
        Write-Warning "Not found in gradle cache: $coord"
        continue
    }
    $destDir = Join-Path $m2 (Convert-GroupPath $group); $destDir = Join-Path $destDir $artifact; $destDir = Join-Path $destDir $version
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
    $script:copied = 0
    Get-ChildItem -Path $srcDir -Recurse -File | ForEach-Object {
        $fname = $_.Name
        # Gradle cache stores files under hash dirs with the same file name as the artifact.
        $target = Join-Path $destDir $fname
        if ($fname -like "$artifact-$version*.jar" -or $fname -like "$artifact-$version*.pom" -or $fname -like "$artifact-$version*.module") {
            Copy-Item -Force $_.FullName $target
            $script:copied++
        }
    }
    Write-Output "Staged $coord -> $destDir ($script:copied files)"
}
