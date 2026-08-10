[CmdletBinding()]
param(
	[Parameter(Mandatory = $true)]
	[ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
	[string] $JarTool,

	[Parameter(Mandatory = $true)]
	[ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
	[string] $CoreJar,

	[Parameter(Mandatory = $true)]
	[ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
	[string] $OriginalMtrJar,

	[Parameter(Mandatory = $true)]
	[string] $OutputJar
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$jarToolPath = (Resolve-Path -LiteralPath $JarTool).Path
$coreJarPath = (Resolve-Path -LiteralPath $CoreJar).Path
$originalMtrJarPath = (Resolve-Path -LiteralPath $OriginalMtrJar).Path
$outputJarPath = [System.IO.Path]::GetFullPath($OutputJar)

if (Test-Path -LiteralPath $outputJarPath) {
	throw "Output already exists: $outputJarPath"
}

$classEntries = @(
	'org/mtr/core/Main.class',
	'org/mtr/core/data/Rail.class',
	'org/mtr/core/data/Siding.class',
	'org/mtr/core/data/Vehicle.class',
	'org/mtr/core/simulation/FileLoader.class',
	'org/mtr/core/simulation/Simulator.class',
	'org/mtr/core/tool/Utilities.class'
)

$coreEntries = @(& $jarToolPath tf $coreJarPath)
$mtrEntries = @(& $jarToolPath tf $originalMtrJarPath)
foreach ($entry in $classEntries) {
	if ($entry -notin $coreEntries) {
		throw "Patched core class is missing from the Core JAR: $entry"
	}
	if ($entry -notin $mtrEntries) {
		throw "Target class is missing from the MTR JAR: $entry"
	}
}

$signatureEntries = @($mtrEntries | Where-Object { $_ -match '^META-INF/[^/]+\.(SF|RSA|DSA|EC)$' })
if ($signatureEntries.Count -gt 0) {
	throw "Refusing to modify a signed MTR JAR: $($signatureEntries -join ', ')"
}

$outputDirectory = Split-Path -Parent $outputJarPath
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
Copy-Item -LiteralPath $originalMtrJarPath -Destination $outputJarPath

$patchedClasses = @{}
$coreArchive = [System.IO.Compression.ZipFile]::OpenRead($coreJarPath)
try {
	foreach ($entry in $classEntries) {
		$coreEntry = $coreArchive.GetEntry($entry)
		if ($null -eq $coreEntry) {
			throw "Failed to open patched Core class: $entry"
		}
		$sourceStream = $coreEntry.Open()
		$memoryStream = [System.IO.MemoryStream]::new()
		try {
			$sourceStream.CopyTo($memoryStream)
			$patchedClasses[$entry] = $memoryStream.ToArray()
		} finally {
			$memoryStream.Dispose()
			$sourceStream.Dispose()
		}
	}
} finally {
	$coreArchive.Dispose()
}

$outputArchive = [System.IO.Compression.ZipFile]::Open($outputJarPath, [System.IO.Compression.ZipArchiveMode]::Update)
try {
	foreach ($entry in $classEntries) {
		$existingEntries = @($outputArchive.Entries | Where-Object FullName -EQ $entry)
		foreach ($existingEntry in $existingEntries) {
			$existingEntry.Delete()
		}

		$newEntry = $outputArchive.CreateEntry($entry, [System.IO.Compression.CompressionLevel]::Optimal)
		$destinationStream = $newEntry.Open()
		try {
			$bytes = [byte[]] $patchedClasses[$entry]
			$destinationStream.Write($bytes, 0, $bytes.Length)
		} finally {
			$destinationStream.Dispose()
		}
	}
} finally {
	$outputArchive.Dispose()
}

$outputEntries = @(& $jarToolPath tf $outputJarPath)
foreach ($entry in $classEntries) {
	if ($entry -notin $outputEntries) {
		throw "Patched output is missing class: $entry"
	}
}

Get-FileHash -LiteralPath $outputJarPath -Algorithm SHA256
