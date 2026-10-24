#!/system/bin/sh
project=$(cat $TMPDIR/DNA.ini)
# Internally store the project catalog
DNA_PRO=$DNA_DIR/$project
# External engineering catalogs
DNA_TMP=$DNA_TMP/$project
# imgInformation Catalog
configdir="$DNA_TMP/config"
# The plug-in tool is stored in the directory, and super is the name of your plug-in folder, which you can modify by yourself
#SoutaEver
subbin=$START_DIR/tools
# Give permissions to the tool
# chmod -R 755 $subbin/*
# The following is free to play, as long as the tool can work
cd $DNA_TMP

echo "
  _                               
 |_)  _  ._ _                     
 | \ (_) |_|.|   ._ _|_ o  _  ._  
 |_/ (/_ (_ | \/ |_) |_ | (_) | | 
              /  |                
  "
echo "Processing... This may take some time"
echo""

# Define file path
for FSTAB in "$DNA_TMP/vendor/etc/fstab"*; do
    echo "Processing: $(basename "$FSTAB")"

    # Check if the file exists
    if [[ -f "$FSTAB" ]]; then
        # Replace 'fileencryption' with 'fileencryptable'
        if grep -q 'fileencryption' "$FSTAB"; then
            sed -i 's/fileencryption/fileencryptable/g' "$FSTAB"
            echo "Successfully replaced 'fileencryption' with 'fileencryptable' in $(basename "$FSTAB")"
        else
            echo "No 'fileencryption' found in $(basename "$FSTAB"), nothing to replace"
        fi
    else
        echo "Warning: $(basename "$FSTAB") not found, skipping"
    fi
done