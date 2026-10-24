#Project
CK () {
for i in $(ls -d $DNA_DIR/DNA_*);do
  echo "${i##*/}"
done
}
#Create a new project
XJ () {
if [ -d $DNA_DIR/DNA_"$T" ]; then
  echo "> The project already exists and will be renamed automatically! "
  T="$T"-`date "+%Y%m%d%H%M%S"`
  echo "> Creating：DNA_${T}"
  mkdir -p $DNA_DIR/DNA_"$T"
else
  echo "> Creating：DNA_${T}"
  mkdir -p $DNA_DIR/DNA_"$T"
fi
echo "> Created successfully! "
echo "DNA_${T}" > $TMPDIR/DNA.ini
}
#Delete the project
SC () {
for i in ${TSS};do
  echo "> Deleting：${i}"
  rm -rf $DNA_DIR/$i
  rm -rf $DNA_TMP/$i
  if [ "$i" = "$project" ];then
    rm -rf $TMPDIR/DNA.ini
  fi
  echo "> Deleted complete!"
done
}
#Delete the plugin
sub (){
for i in ${sub};do
  echo "> Deleting：$i"
  rm -rf $START_DIR/module/$i
  echo "> Deleted successfully！"
done
}

$1