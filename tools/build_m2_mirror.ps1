$gradleBase = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"
$m2 = "$env:USERPROFILE\.m2\repository"

$allowedExt = @(".jar", ".aar", ".pom", ".module", ".zip")
$skipped = 0
$copied = 0

Get-ChildItem -Path $gradleBase -Directory | ForEach-Object {
    $groupDir = $_
    $group = $groupDir.Name
    Get-ChildItem -Path $groupDir.FullName -Directory | ForEach-Object {
        $artifactDir = $_
        $artifact = $artifactDir.Name
        Get-ChildItem -Path $artifactDir.FullName -Directory | ForEach-Object {
            $versionDir = $_
            $version = $versionDir.Name
            Get-ChildItem -Path $versionDir.FullName -Recurse -File | ForEach-Object {
                $fname = $_.Name
                $ext = $_.Extension.ToLower()
                # Copy every main artifact file (jar/aar/pom/module/zip). KMP/Android libraries name
                # underlying artifacts like "ui-release.aar", so we cannot rely on <artifact>-<version>.
                # Skip optional classifiers (sources/javadoc/samples/tests).
                if (($allowedExt -contains $ext) -and
                    $fname -notmatch '-(sources|javadoc|samples|tests|internals|metadata)(\.|$)') {
                    $destDir = Join-Path $m2 ($group -replace '\.', '\')
                    $destDir = Join-Path $destDir $artifact
                    $destDir = Join-Path $destDir $version
                    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
                    Copy-Item -Force $_.FullName (Join-Path $destDir $fname)
                    $script:copied++
                } else {
                    $script:skipped++
                }
            }
        }
    }
}
Write-Output "Mirror complete. copied=$copied skipped=$skipped"
