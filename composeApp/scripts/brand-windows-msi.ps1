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

function Escape-MsiSqlString([string] $Value) {
    return $Value.Replace("'", "''")
}

function Ensure-ActionTextTable([Microsoft.Deployment.WindowsInstaller.Database] $Database) {
    if ($Database.Tables.Contains("ActionText")) {
        return
    }

    $Database.Execute("CREATE TABLE ``ActionText`` (``Action`` CHAR(72) NOT NULL, ``Description`` CHAR(255) LOCALIZABLE, ``Template`` CHAR(255) LOCALIZABLE PRIMARY KEY ``Action``)")
}

function Set-ActionTextRow(
    [Microsoft.Deployment.WindowsInstaller.Database] $Database,
    [string] $Action,
    [string] $Description,
    [string] $Template
) {
    $actionValue = Escape-MsiSqlString $Action
    $descriptionValue = Escape-MsiSqlString $Description
    $templateValue = Escape-MsiSqlString $Template

    $existing = $Database.ExecuteStringQuery("SELECT ``Action`` FROM ``ActionText`` WHERE ``Action`` = '$actionValue'")
    if ($existing.Count -gt 0) {
        $Database.Execute("UPDATE ``ActionText`` SET ``Description`` = '$descriptionValue', ``Template`` = '$templateValue' WHERE ``Action`` = '$actionValue'")
    } else {
        $Database.Execute("INSERT INTO ``ActionText`` (``Action``, ``Description``, ``Template``) VALUES ('$actionValue', '$descriptionValue', '$templateValue')")
    }
}

function Test-MsiRowExists(
    [Microsoft.Deployment.WindowsInstaller.Database] $Database,
    [string] $Query
) {
    $view = $Database.OpenView($Query)
    try {
        $view.Execute()
        $record = $view.Fetch()
        if ($record -eq $null) {
            return $false
        }
        $record.Close()
        return $true
    } finally {
        $view.Close()
    }
}

function Execute-MsiSqlIfMissing(
    [Microsoft.Deployment.WindowsInstaller.Database] $Database,
    [string] $ExistsQuery,
    [string] $InsertQuery
) {
    if (-not (Test-MsiRowExists $Database $ExistsQuery)) {
        $Database.Execute($InsertQuery)
    }
}

function Set-ControlNext(
    [Microsoft.Deployment.WindowsInstaller.Database] $Database,
    [string] $Dialog,
    [string] $Control,
    [string] $NextControl
) {
    $dialogValue = Escape-MsiSqlString $Dialog
    $controlValue = Escape-MsiSqlString $Control
    $nextControlValue = Escape-MsiSqlString $NextControl
    $Database.Execute("UPDATE ``Control`` SET ``Control_Next`` = '$nextControlValue' WHERE ``Dialog_`` = '$dialogValue' AND ``Control`` = '$controlValue'")
}

function Set-MsiProperty(
    [Microsoft.Deployment.WindowsInstaller.Database] $Database,
    [string] $Property,
    [string] $Value
) {
    $propertyValue = Escape-MsiSqlString $Property
    $escapedValue = Escape-MsiSqlString $Value
    $existing = $Database.ExecuteStringQuery("SELECT ``Property`` FROM ``Property`` WHERE ``Property`` = '$propertyValue'")
    if ($existing.Count -gt 0) {
        $Database.Execute("UPDATE ``Property`` SET ``Value`` = '$escapedValue' WHERE ``Property`` = '$propertyValue'")
    } else {
        $Database.Execute("INSERT INTO ``Property`` (``Property``, ``Value``) VALUES ('$propertyValue', '$escapedValue')")
    }
}

function Add-SecureCustomProperty(
    [Microsoft.Deployment.WindowsInstaller.Database] $Database,
    [string] $Property
) {
    $current = $Database.ExecuteStringQuery("SELECT ``Value`` FROM ``Property`` WHERE ``Property`` = 'SecureCustomProperties'") |
        Select-Object -First 1
    $properties = @()
    if ($current) {
        $properties = $current.Split(";") | Where-Object { $_.Trim().Length -gt 0 }
    }
    if ($properties -notcontains $Property) {
        Set-MsiProperty $Database "SecureCustomProperties" (($properties + $Property) -join ";")
    }
}

function Add-UninstallDataCleanupUi([Microsoft.Deployment.WindowsInstaller.Database] $Database) {
    Set-MsiProperty $Database "CLEAR_NUVIO_DATA" "0"
    Add-SecureCustomProperty $Database "CLEAR_NUVIO_DATA"

    Execute-MsiSqlIfMissing $Database `
        "SELECT ``Property`` FROM ``CheckBox`` WHERE ``Property`` = 'CLEAR_NUVIO_DATA'" `
        "INSERT INTO ``CheckBox`` (``Property``, ``Value``) VALUES ('CLEAR_NUVIO_DATA', '1')"

    Execute-MsiSqlIfMissing $Database `
        "SELECT ``Dialog_`` FROM ``Control`` WHERE ``Dialog_`` = 'MaintenanceTypeDlg' AND ``Control`` = 'ClearNuvioData'" `
        "INSERT INTO ``Control`` (``Dialog_``, ``Control``, ``Type``, ``X``, ``Y``, ``Width``, ``Height``, ``Attributes``, ``Property``, ``Text``, ``Control_Next``) VALUES ('MaintenanceTypeDlg', 'ClearNuvioData', 'CheckBox', 40, 211, 305, 18, 2, 'CLEAR_NUVIO_DATA', 'Delete local profile data and logs when removing Nuvio', 'Back')"
    Set-ControlNext $Database "MaintenanceTypeDlg" "RemoveButton" "ClearNuvioData"
    Set-ControlNext $Database "MaintenanceTypeDlg" "ClearNuvioData" "Back"

    Execute-MsiSqlIfMissing $Database `
        "SELECT ``Dialog_`` FROM ``Control`` WHERE ``Dialog_`` = 'VerifyReadyDlg' AND ``Control`` = 'ClearNuvioData'" `
        "INSERT INTO ``Control`` (``Dialog_``, ``Control``, ``Type``, ``X``, ``Y``, ``Width``, ``Height``, ``Attributes``, ``Property``, ``Text``, ``Control_Next``) VALUES ('VerifyReadyDlg', 'ClearNuvioData', 'CheckBox', 25, 154, 320, 18, 2, 'CLEAR_NUVIO_DATA', 'Delete local Nuvio profile data and logs for this Windows user', 'RemoveNoShield')"
    Set-ControlNext $Database "VerifyReadyDlg" "Remove" "ClearNuvioData"
    Set-ControlNext $Database "VerifyReadyDlg" "ClearNuvioData" "RemoveNoShield"

    Execute-MsiSqlIfMissing $Database `
        "SELECT ``Dialog_`` FROM ``Control`` WHERE ``Dialog_`` = 'VerifyReadyDlg' AND ``Control`` = 'ClearNuvioDataNote'" `
        "INSERT INTO ``Control`` (``Dialog_``, ``Control``, ``Type``, ``X``, ``Y``, ``Width``, ``Height``, ``Attributes``, ``Text``) VALUES ('VerifyReadyDlg', 'ClearNuvioDataNote', 'Text', 43, 176, 305, 34, 2, 'This removes data under %APPDATA%\Nuvio and %LOCALAPPDATA%\Nuvio, including profiles, logs, saved add-ons, and connected-service credentials.')"

    foreach ($control in @("ClearNuvioData", "ClearNuvioDataNote")) {
        Execute-MsiSqlIfMissing $Database `
            "SELECT ``Dialog_`` FROM ``ControlCondition`` WHERE ``Dialog_`` = 'VerifyReadyDlg' AND ``Control_`` = '$control' AND ``Action`` = 'Show'" `
            "INSERT INTO ``ControlCondition`` (``Dialog_``, ``Control_``, ``Action``, ``Condition``) VALUES ('VerifyReadyDlg', '$control', 'Show', 'WixUI_InstallMode = `"Remove`"')"
        Execute-MsiSqlIfMissing $Database `
            "SELECT ``Dialog_`` FROM ``ControlCondition`` WHERE ``Dialog_`` = 'VerifyReadyDlg' AND ``Control_`` = '$control' AND ``Action`` = 'Hide'" `
            "INSERT INTO ``ControlCondition`` (``Dialog_``, ``Control_``, ``Action``, ``Condition``) VALUES ('VerifyReadyDlg', '$control', 'Hide', 'WixUI_InstallMode <> `"Remove`"')"
    }
}

function Add-UninstallDataCleanupActions([Microsoft.Deployment.WindowsInstaller.Database] $Database) {
    Add-SecureCustomProperty $Database "CLEAR_NUVIO_DATA"

    Execute-MsiSqlIfMissing $Database `
        "SELECT ``Action`` FROM ``CustomAction`` WHERE ``Action`` = 'SetNuvioRoamingDataDir'" `
        "INSERT INTO ``CustomAction`` (``Action``, ``Type``, ``Source``, ``Target``) VALUES ('SetNuvioRoamingDataDir', 51, 'NUVIO_ROAMING_DATA_DIR', '[%APPDATA]\Nuvio')"
    Execute-MsiSqlIfMissing $Database `
        "SELECT ``Action`` FROM ``CustomAction`` WHERE ``Action`` = 'SetNuvioLocalDataDir'" `
        "INSERT INTO ``CustomAction`` (``Action``, ``Type``, ``Source``, ``Target``) VALUES ('SetNuvioLocalDataDir', 51, 'NUVIO_LOCAL_DATA_DIR', '[%LOCALAPPDATA]\Nuvio')"

    $cleanupCondition = 'CLEAR_NUVIO_DATA = "1" AND REMOVE~="ALL"'
    Execute-MsiSqlIfMissing $Database `
        "SELECT ``Action`` FROM ``InstallExecuteSequence`` WHERE ``Action`` = 'SetNuvioRoamingDataDir'" `
        "INSERT INTO ``InstallExecuteSequence`` (``Action``, ``Condition``, ``Sequence``) VALUES ('SetNuvioRoamingDataDir', '$cleanupCondition', 770)"
    Execute-MsiSqlIfMissing $Database `
        "SELECT ``Action`` FROM ``InstallExecuteSequence`` WHERE ``Action`` = 'SetNuvioLocalDataDir'" `
        "INSERT INTO ``InstallExecuteSequence`` (``Action``, ``Condition``, ``Sequence``) VALUES ('SetNuvioLocalDataDir', '$cleanupCondition', 771)"

    $component = $Database.ExecuteStringQuery("SELECT ``Component`` FROM ``Component``") | Select-Object -First 1
    if ($component) {
        Execute-MsiSqlIfMissing $Database `
            "SELECT ``WixRemoveFolderEx`` FROM ``WixRemoveFolderEx`` WHERE ``WixRemoveFolderEx`` = 'NuvioRemoveRoamingData'" `
            "INSERT INTO ``WixRemoveFolderEx`` (``WixRemoveFolderEx``, ``Component_``, ``Property``, ``InstallMode``) VALUES ('NuvioRemoveRoamingData', '$component', 'NUVIO_ROAMING_DATA_DIR', 2)"
        Execute-MsiSqlIfMissing $Database `
            "SELECT ``WixRemoveFolderEx`` FROM ``WixRemoveFolderEx`` WHERE ``WixRemoveFolderEx`` = 'NuvioRemoveLocalData'" `
            "INSERT INTO ``WixRemoveFolderEx`` (``WixRemoveFolderEx``, ``Component_``, ``Property``, ``InstallMode``) VALUES ('NuvioRemoveLocalData', '$component', 'NUVIO_LOCAL_DATA_DIR', 2)"
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
    Ensure-ActionTextTable $database
    Set-ActionTextRow $database "InstallFiles" "Installing Nuvio application files and bundled player runtime..." ""
    Set-ActionTextRow $database "RemoveFiles" "Removing Nuvio application files..." ""
    Add-UninstallDataCleanupUi $database
    Add-UninstallDataCleanupActions $database
    $database.Commit()
} finally {
    $database.Close()
}

Write-Host "Branded MSI artwork: $msi"
