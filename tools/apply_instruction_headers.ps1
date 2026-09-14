#requires -Version 5.1
# Adds the ManageMyOpz standard instruction header to every canonical source
# file (py, java, js, jsx, ts, tsx, dart). Idempotent: skips files that already
# contain the header. Preserves Python shebang lines.

$ErrorActionPreference = 'Stop'
$root = 'k:\technosprint\managemyopz\initial\managemyopz'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$extensions = @('.py', '.java', '.js', '.jsx', '.ts', '.tsx', '.dart')
$marker = 'Organization: Technosprint'
$created = '2026-09-01'
$owner = 'Logaraj S'
$org = 'Technosprint info Solutions'

$changed = 0
$skipped = 0

$files = Get-ChildItem -Path $root -Recurse -File | Where-Object {
    $extensions -contains $_.Extension -and
    $_.FullName -notmatch '\\(node_modules|\.git|generated|dist|build|target)\\' -and
    $_.FullName -notmatch '\\common\\mobile\\lib\\modules\\'
}

foreach ($file in $files) {
    $content = [System.IO.File]::ReadAllText($file.FullName)
    if ($content.Contains($marker)) { $skipped++; continue }

    switch ($file.Extension) {
        '.java' {
            $header = "/*`r`n * Organization: $org`r`n * Owner: $owner`r`n * Created at: $created`r`n * Description: Governed by ManageMyOpz Java coding standards.`r`n */`r`n"
            $content = $header + $content
        }
        { $_ -in @('.js', '.jsx', '.ts', '.tsx') } {
            $header = "/**`r`n * Organization: $org`r`n * Owner: $owner`r`n * Created at: $created`r`n * Description: Governed by ManageMyOpz React coding standards.`r`n */`r`n"
            $content = $header + $content
        }
        '.dart' {
            $header = "// Organization: $org`r`n// Owner: $owner`r`n// Created at: $created`r`n// Description: Governed by ManageMyOpz Flutter coding standards.`r`n"
            $content = $header + $content
        }
        '.py' {
            $header = "# Organization: $org`r`n# Owner: $owner`r`n# Created at: $created`r`n# Description: Governed by ManageMyOpz Python coding standards.`r`n"
            if ($content -match '^(#![^\r\n]*\r?\n)') {
                $shebang = $Matches[1]
                $content = $shebang + $header + $content.Substring($shebang.Length)
            } else {
                $content = $header + $content
            }
        }
    }

    [System.IO.File]::WriteAllText($file.FullName, $content, $utf8NoBom)
    $changed++
}

Write-Output "HEADER_APPLY_DONE changed=$changed skipped=$skipped total=$($files.Count)"
