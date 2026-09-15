$password = 'm67#qGJfTM6^7Z3kifUaMK7!'
$loginBody = @{
    username = "admin@technosprint.net"
    password = $password
} | ConvertTo-Json

Write-Host "=================================================="
Write-Host "1. Testing Authentication..."
$login = Invoke-RestMethod -Uri "http://localhost:8114/api/v1/opzhub/identity/login" -Method Post -Body $loginBody -ContentType "application/json" -SessionVariable s
Write-Host "   Login success:" $login.ok
Write-Host "   User:" $login.data.user.n "(" $login.data.user.r ")"
Write-Host "   Session token:" $login.data.token

Write-Host "`n2. Testing Create Lead (POST /manage-my-market/leads)..."
$newLead = @{
    displayName = "Acme Corp Expansion"
    firstName = "John"
    lastName = "Doe"
    companyName = "Acme Corp"
    email = "jdoe@acmecorp.com"
    phone = "+1-555-0199"
    leadSourceType = "MANUAL"
    estimatedValue = 50000
    priority = "HOT"
    notes = "High potential customer from marketing event."
} | ConvertTo-Json

$createdLead = Invoke-RestMethod -Uri "http://localhost:8114/api/v1/opzhub/manage-my-market/leads" -Method Post -Body $newLead -ContentType "application/json" -WebSession $s
Write-Host "   Create lead success:" $createdLead.ok
Write-Host "   Created Lead ID:" $createdLead.data.id
Write-Host "   Created Lead Code:" $createdLead.data.lead_code
Write-Host "   Lead Status:" $createdLead.data.status

Write-Host "`n3. Testing Read Leads (POST /manage-my-market/leads/read)..."
$leads = Invoke-RestMethod -Uri "http://localhost:8114/api/v1/opzhub/manage-my-market/leads/read" -Method Post -Body "{}" -ContentType "application/json" -WebSession $s
Write-Host "   Leads query success:" $leads.ok
Write-Host "   Total Leads Returned:" $leads.data.Count
Write-Host "   First Lead:" $leads.data[0].display_name "(" $leads.data[0].lead_code ")"

Write-Host "`n4. Testing Campaigns (POST /manage-my-market/campaigns/read)..."
$campaigns = Invoke-RestMethod -Uri "http://localhost:8114/api/v1/opzhub/manage-my-market/campaigns/read" -Method Post -Body "{}" -ContentType "application/json" -WebSession $s
Write-Host "   Campaigns query success:" $campaigns.ok "Count:" $campaigns.data.Count

Write-Host "`n5. Testing Journeys (POST /manage-my-market/journeys/read)..."
$journeys = Invoke-RestMethod -Uri "http://localhost:8114/api/v1/opzhub/manage-my-market/journeys/read" -Method Post -Body "{}" -ContentType "application/json" -WebSession $s
Write-Host "   Journeys query success:" $journeys.ok "Count:" $journeys.data.Count

Write-Host "`n6. Testing Call Queues (POST /manage-my-market/call-queues/read)..."
$calls = Invoke-RestMethod -Uri "http://localhost:8114/api/v1/opzhub/manage-my-market/call-queues/read" -Method Post -Body "{}" -ContentType "application/json" -WebSession $s
Write-Host "   Call Queues query success:" $calls.ok "Count:" $calls.data.Count

Write-Host "`n7. Testing Referrers (POST /manage-my-market/referrers/read)..."
$referrers = Invoke-RestMethod -Uri "http://localhost:8114/api/v1/opzhub/manage-my-market/referrers/read" -Method Post -Body "{}" -ContentType "application/json" -WebSession $s
Write-Host "   Referrers query success:" $referrers.ok "Count:" $referrers.data.Count

Write-Host "`n8. Testing Widget Data (GET /manage-my-market/widgets/mkt_kpi_overview)..."
$widget = Invoke-RestMethod -Uri "http://localhost:8114/api/v1/opzhub/manage-my-market/widgets/mkt_kpi_overview" -Method Get -WebSession $s
Write-Host "   Widget query success:" $widget.ok "Widget ID:" $widget.data.widget_id

Write-Host "`n9. Testing Reports (POST /manage-my-market/reports/lead-summary)..."
$report = Invoke-RestMethod -Uri "http://localhost:8114/api/v1/opzhub/manage-my-market/reports/lead-summary" -Method Post -Body "{}" -ContentType "application/json" -WebSession $s
Write-Host "   Lead Summary Report success:" $report.ok "Summary Rows:" $report.data.Count
Write-Host "=================================================="
Write-Host "ALL CHECKS COMPLETED SUCCESSFULLY!"
