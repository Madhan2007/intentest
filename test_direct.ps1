$body = @{username='admin@technosprint.net'; password='m67#qGJfTM6^7Z3kifUaMK7!'} | ConvertTo-Json
$login = Invoke-RestMethod -Uri 'http://localhost:8114/api/v1/opzhub/identity/login' -Method Post -Body $body -ContentType 'application/json' -SessionVariable s
Write-Host "Login result:" ($login | ConvertTo-Json -Compress)

try {
    $res = Invoke-WebRequest -Uri 'http://localhost:8114/api/v1/opzhub/manage-my-market/leads/read' -Method Post -Body '{}' -ContentType 'application/json' -WebSession $s
    Write-Host "Leads response:" $res.Content
} catch {
    Write-Host "Leads failed with status:" $_.Exception.Response.StatusCode.value__
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Host "Error Body:" $reader.ReadToEnd()
}
