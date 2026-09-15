$loginBody = @{
    username = "admin@technosprint.net"
    password = 'm67#qGJfTM6^7Z3kifUaMK7!'
} | ConvertTo-Json

$login = Invoke-RestMethod -Method Post -Uri "http://localhost:8114/api/v1/opzhub/identity/login" -ContentType "application/json" -Body $loginBody -SessionVariable s
$apps = Invoke-RestMethod -Method Get -Uri "http://localhost:8114/api/v1/opzhub/apps" -WebSession $s
$marketApp = $apps.data.applications | Where-Object { $_.appKey -like "*market*" }
$marketApp | Format-List
