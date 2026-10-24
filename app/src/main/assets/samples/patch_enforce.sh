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
cd $DNA_TMP

echo "
  _                                   
 |_) _. _|_  _ |_                     
 |_ (_|  |_ (_ | |                    
 |_) ._ o __  _. ._  ._               
 |   |  | \/ (_| |_) |_)              
  _            | |   |                
 |_) _  ._ ._ _  o  _  _ o  _  ._   _ 
 |  (/_ |  | | | | _> _> | (_) | | _> 
                                      
  "
echo "Processing... This may take some time"
echo""

# ---- Now modify vendor/build.prop after the script finishes ----

# Define the build.prop file location inside vendor
BUILD_PROP="$DNA_TMP/vendor/build.prop"

# Check if the build.prop file exists
if [[ -f "$BUILD_PROP" ]]; then
    # Remove the line containing 'ro.control_privapp_permissions' if present
    sed -i '/^ro.control_privapp_permissions/d' "$BUILD_PROP"
    echo "Successfully removed 'ro.control_privapp_permissions' from $BUILD_PROP"
else
    echo "Error: vendor/build.prop not found at $BUILD_PROP"
    exit 1
fi
#SoutaEver