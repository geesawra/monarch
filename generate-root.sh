#!/bin/bash

# Check if a file path argument is provided
if [ $# -eq 0 ]; then
    echo "Usage: $0 <path-to-apk-file>"
    exit 1
fi

# Get the input file path
INPUT_APK="$1"

# Check if the file exists
if [ ! -f "$INPUT_APK" ]; then
    echo "Error: File '$INPUT_APK' not found"
    exit 1
fi

# Get the current UNIX timestamp
TIMESTAMP=$(date +%s)

# Move APK file to its new home
mv "$INPUT_APK" monarch-release-$TIMESTAMP.apk

# Export the final APK path as an environment variable
export MONARCH_APK_PATH="$(pwd)/monarch-release-$TIMESTAMP.apk"
export MONARCH_APK_FILENAME="monarch-release-$TIMESTAMP.apk"

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
    <a href="/monarch-release-$TIMESTAMP.apk">monarch-release-$TIMESTAMP.apk</a>
</body>
</html>
EOF

# Print a confirmation message to the console
echo "Successfully created $OUTPUT_FILE"
