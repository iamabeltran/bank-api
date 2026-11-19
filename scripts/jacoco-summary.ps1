<#
Resume la cobertura de JaCoCo por paquete y en total, leyendo target/site/jacoco/jacoco.csv.
Uso (después de correr "mvn clean verify"):
    powershell -ExecutionPolicy Bypass -File scripts\jacoco-summary.ps1
#>
param([string]$Csv = "target/site/jacoco/jacoco.csv")

if (-not (Test-Path $Csv)) {
    Write-Error "No existe $Csv. Ejecuta primero: mvn clean verify (las pruebas deben pasar)."
    exit 1
}

$rows = Import-Csv $Csv

function SumOf($group, $column) {
    $total = 0
    foreach ($r in $group) { $total += [int]$r.$column }
    return $total
}

function Pct($covered, $missed) {
    $all = $covered + $missed
    if ($all -eq 0) { return "n/a" }
    return ("{0:N1}%" -f (100.0 * $covered / $all))
}

$report = foreach ($g in ($rows | Group-Object PACKAGE | Sort-Object Name)) {
    $lc = SumOf $g.Group "LINE_COVERED";   $lm = SumOf $g.Group "LINE_MISSED"
    $bc = SumOf $g.Group "BRANCH_COVERED"; $bm = SumOf $g.Group "BRANCH_MISSED"
    [pscustomobject]@{
        Package  = $g.Name
        Lines    = Pct $lc $lm
        Branches = Pct $bc $bm
        LinesCovered = "$lc/$($lc + $lm)"
        BranchesCovered = "$bc/$($bc + $bm)"
    }
}
$report | Format-Table -AutoSize

$lc = SumOf $rows "LINE_COVERED";   $lm = SumOf $rows "LINE_MISSED"
$bc = SumOf $rows "BRANCH_COVERED"; $bm = SumOf $rows "BRANCH_MISSED"
Write-Host ("TOTAL  lineas: {0} ({1}/{2})   ramas: {3} ({4}/{5})" -f (Pct $lc $lm), $lc, ($lc + $lm), (Pct $bc $bm), $bc, ($bc + $bm))
Write-Host "Informe visual: target\site\jacoco\index.html"
