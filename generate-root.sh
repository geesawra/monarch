#!/bin/bash

# Get the current UNIX timestamp
TIMESTAMP=$(date +%s)

# Define the output filename
OUTPUT_FILE="index.html"

# Create the HTML file with a HERE document
cat > $OUTPUT_FILE <<EOF
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Download</title>
</head>
<body>
    <!-- File generated at UNIX timestamp: $TIMESTAMP -->
    <a href="/monarch-release.apk">Download Monarch APK</a>
</body>
</html>
EOF

# Print a confirmation message to the console
echo "Successfully created $OUTPUT_FILE"
