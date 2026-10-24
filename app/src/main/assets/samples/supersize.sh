# Cetak ukuran partisi super (GB); fallback 8.5
B=/dev/block/by-name/super
G=8.5
if [ -e "$B" ]; then
  S=$(blockdev --getsize64 "$B" 2>/dev/null)
  [ -n "$S" ] && G=$(LC_NUMERIC=C awk "BEGIN {printf \"%.2f\", $S/1073741824}" | sed 's/\.00$//')
fi
echo "$G"
