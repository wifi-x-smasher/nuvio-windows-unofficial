param(
    [Parameter(Mandatory = $true)]
    [string] $MsiPath,

    [Parameter(Mandatory = $true)]
    [string] $AssetsDir,

    [Parameter(Mandatory = $true)]
    [string] $WixToolsetDir
)

$ErrorActionPreference = "Stop"

function Resolve-RequiredFile([string] $Path) {
    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction Stop
    return $resolved.ProviderPath
}

function Update-BinaryTableStream(
    [Microsoft.Deployment.WindowsInstaller.Database] $Database,
    [string] $Table,
    [string] $KeyColumn,
    [string] $DataColumn,
    [string] $Key,
    [string] $SourcePath
) {
    $record = New-Object Microsoft.Deployment.WindowsInstaller.Record 1
    $record.SetStream(1, $SourcePath)
    $view = $Database.OpenView("UPDATE ``$Table`` SET ``$DataColumn`` = ? WHERE ``$KeyColumn`` = '$Key'")
    try {
        $view.Execute($record)
    } finally {
        $view.Close()
        $record.Close()
    }
}

$msi = Resolve-RequiredFile $MsiPath
$assets = Resolve-RequiredFile $AssetsDir
$dtf = Resolve-RequiredFile (Join-Path $WixToolsetDir "Microsoft.Deployment.WindowsInstaller.dll")
$dialogBmp = Resolve-RequiredFile (Join-Path $assets "WixUIDialogBmp.bmp")
$bannerBmp = Resolve-RequiredFile (Join-Path $assets "WixUIBannerBmp.bmp")
$icon = Resolve-RequiredFile (Join-Path $PSScriptRoot "..\src\desktopMain\resources\app-icon.ico")

Add-Type -Path $dtf

$database = New-Object Microsoft.Deployment.WindowsInstaller.Database($msi, [Microsoft.Deployment.WindowsInstaller.DatabaseOpenMode]::Direct)
try {
    Update-BinaryTableStream $database "Binary" "Name" "Data" "WixUI_Bmp_Dialog" $dialogBmp
    Update-BinaryTableStream $database "Binary" "Name" "Data" "WixUI_Bmp_Banner" $bannerBmp
    Update-BinaryTableStream $database "Icon" "Name" "Data" "JpARPPRODUCTICON" $icon
    $database.Commit()
} finally {
    $database.Close()
}

Write-Host "Branded MSI artwork: $msi"
