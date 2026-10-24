#Copy / move one file or folder
#usage: transfer.sh copy|move   (env: SRC = source path, DST_DIR = destination folder)
MODE="$1"
SRC="${SRC%/}"
DST_DIR="${DST_DIR%/}"

[ -e "$SRC" ] || { echo "> Source not found: $SRC"; exit 1; }
[ -d "$DST_DIR" ] || { echo "> Destination not found: $DST_DIR"; exit 1; }
case "$DST_DIR/" in
  "$SRC"/*) echo "> Can't put a folder inside itself"; exit 1;;
esac
if [ "$MODE" = "move" ] && [ "${SRC%/*}" = "$DST_DIR" ]; then
  echo "> Already in this folder, nothing to do"
  exit 0
fi

base="${SRC##*/}"
stem="$base"
ext=""
if [ ! -d "$SRC" ]; then
  case "$base" in
    ?*.*) stem="${base%.*}"; ext=".${base##*.}";;
  esac
fi
dst="$DST_DIR/$base"
i=1
while [ -e "$dst" ]; do
  dst="$DST_DIR/${stem}_$i$ext"
  i=$((i+1))
done
[ "$dst" = "$DST_DIR/$base" ] || echo "> Name already exists, saving as: ${dst##*/}"

if [ "$MODE" = "move" ]; then
  echo "> Moving: $SRC"
  echo ">     to: $dst"
  sdev=$(stat -c %d "$SRC" 2>/dev/null)
  ddev=$(stat -c %d "$DST_DIR" 2>/dev/null)
  # same filesystem: plain rename (instant)
  if [ -n "$sdev" ] && [ "$sdev" = "$ddev" ] && mv "$SRC" "$dst"; then
    echo "> Done"
    exit 0
  fi
  [ -e "$dst" ] && { echo "> Move failed, check: $dst"; exit 1; }
  # different filesystem (e.g. internal -> SD): copy first, delete source only if copy succeeded
  if cp -r "$SRC" "$dst"; then
    rm -rf "$SRC"
    echo "> Done"
  else
    rm -rf "$dst"
    echo "> Move failed, source kept"
    exit 1
  fi
else
  echo "> Copying: $SRC"
  echo ">      to: $dst"
  if cp -r "$SRC" "$dst"; then
    echo "> Done"
  else
    rm -rf "$dst"
    echo "> Copy failed"
    exit 1
  fi
fi
