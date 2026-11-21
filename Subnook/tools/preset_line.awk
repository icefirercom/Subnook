{
  c=$4; sub("#","",c); p=$5; if (index(p, ".") == 0) p = p ".0";
  printf "        Preset(\"%s\", \"%s\", Category.%s, 0xFF%s, %s, \"%s\", %s, CycleUnit.%s, PayMethod.%s, \"%s\"),\n", $1,$2,$3,c,p,$6,$7,$8,$9,$13
}
