#Copy / move one file or folder
#usage: transfer.sh copy|move   (env: SRC = source path, DST_DIR = destination folder)
MODE="$1"
SRC="${SRC%/}"
DST_DIR="${DST_DIR%/}"
if [ "$MODE" = "move" ]; then LABEL="MOVE"; ING="Moving"; else MODE="copy"; LABEL="COPY"; ING="Copying"; fi

fail() { echo "✕ $1"; exit 1; }

[ -e "$SRC" ] || fail "Source not found: $SRC"
[ -d "$DST_DIR" ] || fail "Destination not found: $DST_DIR"
case "$DST_DIR/" in
  "$SRC"/*) fail "Can't put a folder inside itself";;
esac
if [ "$MODE" = "move" ] && [ "${SRC%/*}" = "$DST_DIR" ]; then
  echo "✓ Already in this folder, nothing to do"
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

size=$(du -sh "$SRC" 2>/dev/null | cut -f1)
[ -n "$size" ] || size="-"
echo "$LABEL"
echo "  Item : $base"
echo "  From : ${SRC%/*}"
echo "  To   : $DST_DIR"
echo "  Size : $size"
[ "$dst" = "$DST_DIR/$base" ] || echo "  Note : name exists, saved as ${dst##*/}"
echo ""

t0=$(date +%s)
finish() { echo "✓ Done in $(( $(date +%s) - t0 ))s"; }
echo "$ING…"

if [ "$MODE" = "move" ]; then
  sdev=$(stat -c %d "$SRC" 2>/dev/null)
  ddev=$(stat -c %d "$DST_DIR" 2>/dev/null)
  # same filesystem: plain rename (instant)
  if [ -n "$sdev" ] && [ "$sdev" = "$ddev" ] && mv "$SRC" "$dst"; then
    finish
    exit 0
  fi
  [ -e "$dst" ] && fail "Move failed, check: $dst"
  # different filesystem (e.g. internal -> SD): copy first, delete source only if copy succeeded
  echo "Different storage: copying first, then removing the original"
  if cp -r "$SRC" "$dst"; then
    rm -rf "$SRC"
    finish
  else
    rm -rf "$dst"
    fail "Move failed, original kept"
  fi
else
  if cp -r "$SRC" "$dst"; then
    finish
  else
    rm -rf "$dst"
    fail "Copy failed"
  fi
fi
