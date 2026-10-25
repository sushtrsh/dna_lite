# Script authored by Han | 情非得已c  
# Made for use in the Gaoki Assistant tool
name(){
# Hanya partisi bernama (by-name) + mapper. Disk utuh (sda/sdb/sdc, mmcblk, nvme) dan
# perangkat loop/ram/zram dilewati supaya tidak ikut masuk daftar backup/flash.
jian="$TEMP_DIR/by_name_v2.log"
rm -f "$TEMP_DIR/by_name.log"
if [ ! -s "$jian" ]; then
    : > "$jian"
    BLOCKDEV=`which blockdev`
    for dir in /dev/block/by-name /dev/block/bootdevice/by-name /dev/block/platform/*/by-name /dev/block/platform/*/*/by-name /dev/block/mapper; do
        [ -d "$dir" ] || continue
        for o in "$dir"/*; do
            [ -L "$o" ] || continue
            c=${o##*/}
            [ "$c" = "control" ] && continue
            tgt=`readlink -f "$o" 2>/dev/null`
            base=${tgt##*/}
            case "$base" in
                sd[a-z]|sd[a-z][a-z]|mmcblk[0-9]|mmcblk[0-9][0-9]|nvme[0-9]*n[0-9]|loop*|ram*|zram*) continue;;
            esac
            echo "$c|$o"
        done
    done | sort -u -t'|' -k1,1 | while IFS='|' read -r Row BLOCK; do
        if [ -n "$BLOCKDEV" ]; then
            size=`blockdev --getsize64 "$BLOCK" 2>/dev/null`
            [ -z "$size" ] && size=0
            if [ $size -ge 1073741824 ]; then
                File_Type=`awk "BEGIN{printf \"%.2f\", $size/1073741824}"`G
            elif [ $size -ge 1048576 ]; then
                File_Type=`awk "BEGIN{printf \"%.1f\", $size/1048576}"`MB
            elif [ $size -ge 1024 ]; then
                File_Type=`awk "BEGIN{printf \"%.0f\", $size/1024}"`kb
            else
                File_Type=${size}b
            fi
            echo "$BLOCK|$Row 「size：$File_Type」" >> "$jian"
        else
            echo "$BLOCK|$Row" >> "$jian"
        fi
    done
fi
cat "$jian"
}

backup(){
Backup=$DNA_DIR/backup
IFS=$'\n'
[ ! -d "$Backup" ] && mkdir -p "$Backup"
echo "Backup folder: $Backup"
ok=0; fail=0
for i in $IMG; do
    e=${i##*/}
    File="$Backup/${e}.img"
    if [ ! -L "$i" ]; then
        echo "！$e backup failed: partition does not exist"
        fail=$((fail+1))
    else
        echo "- Backing up $e partition"
        if dd if="$i" of="$File" bs=4M 2>/dev/null; then
            sz=`ls -lh "$File" 2>/dev/null | awk '{print $5}'`
            echo "✓ Backed up $e to: $File ($sz)"
            ok=$((ok+1))
        else
            echo "！$e backup failed: could not read partition or storage is full"
            rm -f "$File"
            fail=$((fail+1))
        fi
    fi
done
echo "- Finished: $ok backed up, $fail failed"
}

flash(){
IFS=$'\n'
e=${IMG##*/}
echo "- You selected$e partition"
echo "- File path for flashing：$Brush_in"
if [[ ! -L "$IMG" ]];then
    echo "！$e Cannot flash: partition not found"
else
    if [[ -f "$Brush_in" ]]; then
        echo "- Start flashing$e partition"
        dd if="$Brush_in" of="$IMG"
        if [[ $CQ = 1 ]]; then
         echo "Rebooting into recovery mode shortly，Countdown started……"
         for i in $(seq 4 -1 1); do
            echo $i
            sleep 1
         done
         reboot recovery
         fi
         if [[ $CQ1 = 1 ]]; then
          echo "The phone will reboot shortly，Countdown started……"
          for i in $(seq 4 -1 1); do
            echo $i
            sleep 1
          done
          reboot
         fi
    else
        echo "！$Brush_in Flash file does not exist, unable to flash to$e partition"
    fi
    echo "- Finished"
    sleep 2
fi
}
$1