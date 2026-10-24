if [ ! -d $DNA_PRO/out ];then
 mkdir -p $DNA_PRO/out
fi

for i in $ZST; do
    if [ ! -f "$DNA_PRO/$i" ]; then
        echo "File Error $DNA_PRO/$i Not found!"
        continue
    fi
    if expr "$i" : '.*\.zst$' >/dev/null || expr "$i" : '.*\.zstd$' >/dev/null; then
        echo "> Extracting: $i"
        line=$(echo "$i" | cut -d"." -f1)
        zstd -d -k -f $DNA_PRO/$i -o $DNA_PRO/out/$line.img
        echo "> Extraction complete, file is located at: $DNA_PRO/out/$line.img"
    fi
    if expr "$i" : '.*\.img$' >/dev/null; then
        echo "> Compressing：$i "
        zstd -$level -T4 -f $DNA_PRO/$i -o $DNA_PRO/out/$i.zst
        echo "> Compression complete, file is located at：$DNA_PRO/out/$i.zst"
    fi
    if [[ $silence = 1 ]]; then
        echo "> Delete: $i"
        rm -rf "$DNA_PRO/$i"
    fi
done

