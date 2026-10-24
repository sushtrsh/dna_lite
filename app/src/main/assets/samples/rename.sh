#Rename a file / folder / project
#usage: rename.sh item|project   (env: SRC = full path (item) or project folder name (project), NEW = new name)
MODE="$1"
fail() { echo "✕ $1"; exit 1; }

NEW="${NEW%/}"
case "$NEW" in
  ""|.|..|*/*) fail "Invalid name";;
esac

if [ "$MODE" = "project" ]; then
  src="$DNA_DIR/$SRC"
  dst="$DNA_DIR/$NEW"
  label="Project"
else
  src="${SRC%/}"
  dst="${src%/*}/$NEW"
  label="Item"
fi

[ -e "$src" ] || fail "Not found: $src"

base="${src##*/}"
dir="${src%/*}"
size=$(du -sh "$src" 2>/dev/null | cut -f1)
[ -n "$size" ] || size="-"

echo "RENAME"
echo "  $label : $base"
echo "  In   : $dir"
echo "  To   : $NEW"
echo "  Size : $size"
echo ""

if [ "$src" = "$dst" ]; then
  echo "✓ Same name, nothing to do"
  exit 0
fi

lo() { echo "$1" | tr 'A-Z' 'a-z'; }
t0=$(date +%s)
echo "Renaming…"

if [ "$(lo "$src")" = "$(lo "$dst")" ]; then
  # only the letter case changes (FAT / sdcardfs ignore case): go through a temp name
  tmp="${src}.dna_tmp_$$"
  mv "$src" "$tmp" && mv "$tmp" "$dst" || { [ -e "$tmp" ] && mv "$tmp" "$src"; fail "Rename failed"; }
else
  [ -e "$dst" ] && fail "Name already used: $NEW"
  mv "$src" "$dst" || fail "Rename failed"
fi

if [ "$MODE" = "project" ]; then
  # unpacked copy in /data/DNA (or app storage) follows the new name
  if [ -d "$DNA_TMP/$SRC" ] && [ ! -e "$DNA_TMP/$NEW" ]; then
    mv "$DNA_TMP/$SRC" "$DNA_TMP/$NEW" && echo "  Unpacked folder renamed too"
  fi
  if [ "$project" = "$SRC" ]; then
    echo "$NEW" > "$TMPDIR/DNA.ini"
    echo "  Active project updated"
  fi
fi

echo "✓ Done in $(( $(date +%s) - t0 ))s"
