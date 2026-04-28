param(
    [string]$ServiceAccountPath = $env:FIREBASE_SERVICE_ACCOUNT,

    [string]$ProjectId = "sumup-ef352",

    [string]$JsonPath = "$PSScriptRoot\public_source_catalog.json"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ServiceAccountPath)) {
    throw "Service account path is missing. Pass -ServiceAccountPath or set FIREBASE_SERVICE_ACCOUNT."
}

if (!(Test-Path -LiteralPath $ServiceAccountPath)) {
    throw "Service account file not found: $ServiceAccountPath"
}

if (!(Test-Path -LiteralPath $JsonPath)) {
    throw "Catalog JSON file not found: $JsonPath"
}

$serviceAccount = Get-Content -LiteralPath $ServiceAccountPath -Raw | ConvertFrom-Json
$catalog = Get-Content -LiteralPath $JsonPath -Raw | ConvertFrom-Json

if (-not $serviceAccount.client_email -or -not $serviceAccount.private_key) {
    throw "Service account JSON must contain client_email and private_key."
}

if (-not $catalog.groups) {
    throw "Catalog JSON must contain a top-level 'groups' field."
}

foreach ($group in $catalog.groups) {
    if ([string]::IsNullOrWhiteSpace($group.id)) {
        throw "Each group must contain a non-empty 'id'."
    }

    $titleUk = $group.title.uk
    $titleEn = $group.title.en
    if ([string]::IsNullOrWhiteSpace($titleUk) -and [string]::IsNullOrWhiteSpace($group.nameUk) -and [string]::IsNullOrWhiteSpace($group.name)) {
        throw "Group '$($group.id)' must contain a Ukrainian title in 'title.uk', 'nameUk', or 'name'."
    }

    if (-not $group.sources -or $group.sources.Count -eq 0) {
        throw "Group '$($group.id)' must contain at least one source."
    }

    if (-not $group.anchors -or $group.anchors.Count -eq 0) {
        throw "Group '$($group.id)' must contain non-empty 'anchors' for recommendations."
    }
}

function ConvertTo-Base64Url([byte[]]$bytes) {
    [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function ConvertTo-FirestoreValue($value) {
    if ($null -eq $value) {
        return @{ nullValue = $null }
    }

    if ($value -is [bool]) {
        return @{ booleanValue = $value }
    }

    if ($value -is [int] -or $value -is [long]) {
        return @{ integerValue = "$value" }
    }

    if ($value -is [double] -or $value -is [float] -or $value -is [decimal]) {
        return @{ doubleValue = [double]$value }
    }

    if ($value -is [string]) {
        return @{ stringValue = $value }
    }

    if ($value -is [System.Collections.IEnumerable] -and !($value -is [string])) {
        $items = @()
        foreach ($item in $value) {
            $items += ConvertTo-FirestoreValue $item
        }
        return @{ arrayValue = @{ values = $items } }
    }

    if ($value -is [pscustomobject] -or $value -is [hashtable]) {
        $fields = @{}
        foreach ($property in $value.PSObject.Properties) {
            $fields[$property.Name] = ConvertTo-FirestoreValue $property.Value
        }
        return @{ mapValue = @{ fields = $fields } }
    }

    throw "Unsupported Firestore value type: $($value.GetType().FullName)"
}

$now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$header = @{
    alg = "RS256"
    typ = "JWT"
} | ConvertTo-Json -Compress

$claimSet = @{
    iss = $serviceAccount.client_email
    scope = "https://www.googleapis.com/auth/datastore"
    aud = "https://oauth2.googleapis.com/token"
    exp = $now + 3600
    iat = $now
} | ConvertTo-Json -Compress

$headerB64 = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($header))
$claimB64 = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($claimSet))
$unsignedToken = "$headerB64.$claimB64"

$rsa = [System.Security.Cryptography.RSA]::Create()
$privateKey = $serviceAccount.private_key.Replace("-----BEGIN PRIVATE KEY-----", "").Replace("-----END PRIVATE KEY-----", "").Replace("`n", "").Replace("`r", "")
$privateKeyBytes = [Convert]::FromBase64String($privateKey)
$bytesRead = 0
try {
    $null = $rsa.ImportPkcs8PrivateKey($privateKeyBytes, [ref]$bytesRead)
} catch {
    throw "Failed to import Firebase private key. Run this script in PowerShell 7+ (pwsh). Original error: $($_.Exception.Message)"
}
$signatureBytes = $rsa.SignData(
    [Text.Encoding]::UTF8.GetBytes($unsignedToken),
    [System.Security.Cryptography.HashAlgorithmName]::SHA256,
    [System.Security.Cryptography.RSASignaturePadding]::Pkcs1
)
$jwt = "$unsignedToken.$(ConvertTo-Base64Url $signatureBytes)"

$tokenResponse = Invoke-RestMethod -Method Post -Uri "https://oauth2.googleapis.com/token" -ContentType "application/x-www-form-urlencoded" -Body @{
    grant_type = "urn:ietf:params:oauth:grant-type:jwt-bearer"
    assertion = $jwt
}

$firestoreFields = @{
    groups = ConvertTo-FirestoreValue $catalog.groups
}

$body = @{
    fields = $firestoreFields
} | ConvertTo-Json -Depth 100

$documentUrl = "https://firestore.googleapis.com/v1/projects/$ProjectId/databases/(default)/documents/public_source_catalog/default"

Invoke-RestMethod -Method Patch -Uri $documentUrl -Headers @{
    Authorization = "Bearer $($tokenResponse.access_token)"
} -ContentType "application/json" -Body $body | Out-Null

Write-Host "Imported public_source_catalog/default from $JsonPath"
