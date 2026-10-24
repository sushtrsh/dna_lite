#!/system/bin/sh
project=$(cat $TMPDIR/DNA.ini)
# Internally store the project catalog
DNA_PRO=$DNA_DIR/$project
# External engineering catalogs
DNA_TMP=$DNA_TMP/$project
# imgInformation Catalog
configdir="$DNA_TMP/config"
# The plug-in tool is stored in the directory, and super is the name of your plug-in folder, which you can modify by yourself
subbin=$START_DIR/tools
# Give permissions to the tool
# chmod -R 755 $subbin/*
# The following is free to play, as long as the tool can work
cd $DNA_TMP

echo "
  _               _       
 |_)  _  ._ _    |_)      
 | \ (_) | | |   | \ \/\/ 
                          
  "
echo "Processing... This may take some time"
echo""

# ---- Now modify /vendor/etc/fstab ----
for FSTAB in "$DNA_TMP/vendor/etc/fstab"*; do
    echo "Processing: $(basename "$FSTAB")"

    # Check if the file exists
    if [[ -f "$FSTAB" ]]; then
        # Check if 'overlay' exists before attempting to delete
        if grep -q 'overlay' "$FSTAB"; then
            sed -i '/overlay/d' "$FSTAB"
            echo "Successfully removed overlay lines from $(basename "$FSTAB")"
        else
            echo "No overlay lines found in $(basename "$FSTAB"), nothing to remove"
        fi
    else
        echo "Warning: $(basename "$FSTAB") not found, skipping"
    fi
done

# ---- Find and delete init.miui.mi_ext.rc in /mi_ext/etc/init/ ----

INIT_MIUI_EXT="$DNA_TMP/mi_ext/etc/init/init.miui.mi_ext.rc"
echo "Processing: $(basename "$INIT_MIUI_EXT")"

# Check if the file exists and delete it
if [[ -f "$INIT_MIUI_EXT" ]]; then
    rm -f "$INIT_MIUI_EXT"
    echo "Successfully removed $(basename "$INIT_MIUI_EXT")"
else
    echo "Warning: $(basename "$INIT_MIUI_EXT") not found, skipping"
fi