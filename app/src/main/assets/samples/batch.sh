#Copy / move several files or folders in one go
#usage: batch.sh copy|move   (env: SRCS = one path per line, DST_DIR = destination folder)
MODE="$1"
[ "$MODE" = "move" ] || MODE="copy"
list="$TMPDIR/batch.lst"
printf '%s\n' "$SRCS" > "$list"
total=0; ok=0; bad=0
while IFS= read -r p; do
  [ -n "$p" ] && total=$((total+1))
done < "$list"

t0=$(date +%s)
n=0
while IFS= read -r p; do
  [ -n "$p" ] || continue
  n=$((n+1))
  echo "[$n/$total]"
  if SRC="$p" sh "$START_DIR/samples/transfer.sh" "$MODE"; then ok=$((ok+1)); else bad=$((bad+1)); fi
  echo ""
done < "$list"
rm -f "$list"

if [ "$bad" -gt 0 ]; then
  echo "✕ $ok of $total done, $bad failed ($(( $(date +%s) - t0 ))s)"
  exit 1
fi
echo "✓ All $total done in $(( $(date +%s) - t0 ))s"
