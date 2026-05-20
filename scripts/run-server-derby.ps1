$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$serverDir = Join-Path $root "im-server"

Push-Location $serverDir
try {
    if (-not (Test-Path "target\im-server-0.1.0-SNAPSHOT.jar")) {
        mvn package -DskipTests
        if ($LASTEXITCODE -ne 0) {
            throw "mvn package failed"
        }
    }

    $env:SPRING_PROFILES_ACTIVE = "derby"
    $env:HTTP_PORT = if ($env:HTTP_PORT) { $env:HTTP_PORT } else { "18080" }
    $env:TCP_PORT = if ($env:TCP_PORT) { $env:TCP_PORT } else { "19090" }
    $env:DB_URL = if ($env:DB_URL) { $env:DB_URL } else { "jdbc:derby:./data/enterprise-im-derby;create=true" }
    $env:DB_USERNAME = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "enterprise_im" }
    $env:DB_PASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "enterprise_im_dev" }
    $env:DB_DRIVER = if ($env:DB_DRIVER) { $env:DB_DRIVER } else { "org.apache.derby.jdbc.EmbeddedDriver" }

    java -jar target\im-server-0.1.0-SNAPSHOT.jar
} finally {
    Pop-Location
}
