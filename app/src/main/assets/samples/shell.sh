echo
echo
echo "==================Starting=================="
echo
echo "${TO}" > SHE.sh
. ./SHE.sh
[ $? -eq 0 ] && echo -e "\n==================Execution successful==================" || echo -e "\n==================Execution failed=================="
echo