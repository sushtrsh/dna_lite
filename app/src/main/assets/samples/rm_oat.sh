#!/system/bin/sh
project=$(cat $TMPDIR/DNA.ini)  

# Internally store the project catalog  
DNA_PRO=$DNA_DIR/$project  

# External engineering catalogs  
DNA_TMP=$DNA_TMP/$project  

# imgInformation Catalog  
configdir="$DNA_TMP/config"  

# The plug-in tool directory  
subbin=$START_DIR  

# Give permissions to the tool (if needed)  
# chmod -R 755 $subbin/*  

# Navigate to the project directory  
cd "$DNA_TMP"  

echo "
  _                                     
 |_)  _  ._ _   _      _     _   _. _|_ 
 | \ (/_ | | | (_) \/ (/_   (_) (_|  |_ 
                                        
  "
echo " Removing oat folder please wait..."

echo "
"

# Find and remove all 'oat' folders  
find . -type d -name "oat" | while read -r dir; do  
    echo "Removing: $dir"  
    rm -rf "$dir"  
done  

echo ""
echo "[✓] Process complete"
echo ""

#SoutaEver