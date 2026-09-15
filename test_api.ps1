$cred = @{
    username = "admin@technosprint.net"
    password = 'm67#qGJfTM6^7Z3kifUaMK7!'
} | ConvertTo-Json

$login = Invoke-RestMethod -Uri "http://localhost:5173/api/v1/opzhub/identity/login" -Method Post -Body $cred -ContentType "application/json" -SessionVariable s
Write-Host "1. Login OK:" $login.ok "User:" $login.data.user.email

$leads = Invoke-RestMethod -Uri "http://localhost:5173/api/v1/opzhub/manage-my-market/leads/read" -Method Post -Body "{}" -ContentType "application/json" -WebSession $s
Write-Host "2. Leads endpoint OK:" $leads.ok "Count:" $leads.data.Count

$campaigns = Invoke-RestMethod -Uri "http://localhost:5173/api/v1/opzhub/manage-my-market/campaigns/read" -Method Post -Body "{}" -ContentType "application/json" -WebSession $s
Write-Host "3. Campaigns endpoint OK:" $campaigns.ok "Count:" $campaigns.data.Count

$journeys = Invoke-RestMethod -Uri "http://localhost:5173/api/v1/opzhub/manage-my-market/journeys/read" -Method Post -Body "{}" -ContentType "application/json" -WebSession $s
Write-Host "4. Journeys endpoint OK:" $journeys.ok "Count:" $journeys.data.Count

$calls = Invoke-RestMethod -Uri "http://localhost:5173/api/v1/opzhub/manage-my-market/calls/queues/read" -Method Post -Body "{}" -ContentType "application/json" -WebSession $s
Write-Host "5. Calls queue endpoint OK:" $calls.ok "Count:" $calls.data.Count

$referrers = Invoke-RestMethod -Uri "http://localhost:5173/api/v1/opzhub/manage-my-market/referrers/read" -Method Post -Body "{}" -ContentType "application/json" -WebSession $s
Write-Host "6. Referrers endpoint OK:" $referrers.ok "Count:" $referrers.data.Count

$widgets = Invoke-RestMethod -Uri "http://localhost:5173/api/v1/opzhub/manage-my-market/widgets/mkt_kpi_overview" -Method Get -WebSession $s
Write-Host "7. Widget endpoint OK:" $widgets.ok "Data:" ($widgets.data | ConvertTo-Json -Compress)

$report = Invoke-RestMethod -Uri "http://localhost:5173/api/v1/opzhub/manage-my-market/reports/lead-summary" -Method Post -Body "{}" -ContentType "application/json" -WebSession $s
Write-Host "8. Lead summary report endpoint OK:" $report.ok "Count:" $report.data.Count
