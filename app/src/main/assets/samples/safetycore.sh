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
  __       _           _           
 (_   _. _|_ _ _|_    /   _  ._ _  
 __) (_|  | (/_ |_ \/ \_ (_) | (/_ 
                   /               
  "
echo "Removing SafetyCore..."

cp -r /data/user/0/com.dna.tools/files/tools/Safetycore* product/app && \
chmod 755 product/app && \
chmod 644 product/app/Safetycore*/*.apk
echo "
"
echo "[✓] Added Safetycore-placeholder! "
echo "
"