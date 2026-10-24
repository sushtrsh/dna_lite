#!/system/bin/sh
project=$(cat $TMPDIR/DNA.ini)
#Internally store the project catalog
DNA_PRO=$DNA_DIR/$project
#External engineering catalogs
DNA_TMP=$DNA_TMP/$project
#imgInformation Catalog
configdir="$DNA_TMP/config"
#The plug-in tool is stored in the directory, and super is the name of your plug-in folder, which you can modify by yourself
subbin=$START_DIR/tools
#Give permissions to the tool
#chmod -R 755 $subbin/*
#The following is free to play, as long as the tool can work
# Navigate to DNA_TMP
cd "$DNA_TMP" || { echo "Error: Failed to access $DNA_TMP"; exit 1; }

echo "
  _  ___  __       _      _   
 | \  |  (_   /\  |_) |  |_   
 |_/__|_ __) /--\ |_) |_ |_   
 / \ |  /\                    
 \_/ | /--\                   
 | | ._   _|  _. _|_  _       
 |_| |_) (_| (_|  |_ (/_      
     |                        
                          
  "
echo "Disabling... OTA Update"
echo""

# ---- Modify XML files inside /product/etc/device_features/ ----

FEATURES_DIR="$DNA_TMP/product/etc/device_features"

# Ensure the directory exists
if [[ ! -d "$FEATURES_DIR" ]]; then
    echo "Error: Directory $FEATURES_DIR not found."
    exit 1
fi

# Find all XML files and modify them
find "$FEATURES_DIR" -type f -name "*.xml" | while read -r xml_file; do
    # Ensure the file is readable
    if [[ -r "$xml_file" ]]; then
        # Debug: Print XML content to check structure
        echo "Checking file: $xml_file"
        grep -E "support_ota_validate" "$xml_file"

        # Try different patterns to find the line
        if grep -Eiq '<bool name=["'\'']support_ota_validate["'\'']>.*true.*</bool>' "$xml_file"; then
    sed -i -E 's#(<bool name=["'\'']support_ota_validate["'\'']>)[[:space:]]*true[[:space:]]*(</bool>)#\1false\2#gI' "$xml_file"
    echo "Updated OTA validation in: $xml_file"
elif grep -Eiq '<bool name=["'\'']support_ota_validate["'\'']>.*false.*</bool>' "$xml_file"; then
    echo "OTA validation is already disabled in: $xml_file"
else
    echo "Warning: No exact match for OTA validation found in: $xml_file"
fi
    else
        echo "Error: Cannot read file $xml_file"
    fi
done

#SoutaEver