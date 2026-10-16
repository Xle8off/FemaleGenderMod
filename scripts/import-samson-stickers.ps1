param(
    [Parameter(Mandatory=$true)][string]$Archive,
    [Parameter(Mandatory=$true)][string[]]$OutputDirectories
)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archivePath=(Resolve-Path -LiteralPath $Archive).Path
$converter=(Get-Command ffmpeg -ErrorAction Stop).Source
$temporary=Join-Path ([System.IO.Path]::GetTempPath()) ('springplus-samson-'+[guid]::NewGuid())
New-Item -ItemType Directory -Path $temporary | Out-Null
$zip=[System.IO.Compression.ZipFile]::OpenRead($archivePath)
try {
    if($zip.Entries.Count -ne 36) { throw 'Expected exactly 36 stickers' }
    $names=@{}
    foreach($entry in $zip.Entries) {
        if($entry.FullName -notmatch '^file_(37109(?:5[7-9]|[6-8][0-9]|9[0-2]))\.webp$' -or $entry.Length -gt 1048576) {
            throw ('Unexpected archive entry: '+$entry.FullName)
        }
        $id=$Matches[1]
        if($names.ContainsKey($id)) { throw 'Duplicate sticker' }
        $names[$id]=$true
        $inputFile=Join-Path $temporary ($id+'.webp')
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$inputFile,$false)
        $png=Join-Path $temporary ($id+'.png')
        # Format conversion only: keep the original dimensions and alpha; no redrawing.
        & $converter -hide_banner -loglevel error -n -i $inputFile -frames:v 1 $png
        if($LASTEXITCODE -ne 0) { throw ('Conversion failed: '+$id) }
        foreach($root in $OutputDirectories) {
            $output=[System.IO.Path]::GetFullPath($root)
            New-Item -ItemType Directory -Force -Path $output | Out-Null
            Copy-Item -LiteralPath $png -Destination (Join-Path $output ($id+'.png'))
        }
    }
    Write-Output ('Imported '+$names.Count+' original static stickers; temporary conversion files: '+$temporary)
} finally { $zip.Dispose() }
