$mavenVersion = "3.9.6"
$mavenZip = "apache-maven-$mavenVersion-bin.zip"
$mavenUrl = "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries/$mavenZip"
$mavenDir = ".maven"

if (-Not (Test-Path "$mavenDir\apache-maven-$mavenVersion\bin\mvn.cmd")) {
    Write-Host "Downloading Maven..."
    Invoke-WebRequest -Uri $mavenUrl -OutFile $mavenZip
    Write-Host "Extracting Maven..."
    Expand-Archive -Path $mavenZip -DestinationPath $mavenDir -Force
    Remove-Item $mavenZip
}

Write-Host "Running game with Maven..."
& "$mavenDir\apache-maven-$mavenVersion\bin\mvn.cmd" clean compile exec:java
