#!/bin/bash
# 스테이지된 변경에 비밀로 보이는 값이 있는지 클로드에게 묻는다.
# 종료 코드: 0 = 없음, 1 = 의심 줄 있음, 2 = 검사 자체가 실패
schema='{"type":"object","properties":{"has_secret":{"type":"boolean"},"findings":{"type":"array","items":{"type":"object","properties":{"file":{"type":"string"},"line":{"type":"integer"},"reason":{"type":"string"}},"required":["file","line","reason"]}}},"required":["has_secret","findings"]}'
err=$(mktemp)

result=$(git diff --cached | claude -p \
  "스테이지된 diff에서 추가된 줄 중 비밀 키, 토큰, 비밀번호로 보이는 값을 찾아 줘. 값은 출력하지 말고 파일, 줄 번호, 이유만 적어." \
  --output-format json --json-schema "$schema" \
  --max-turns 5 --max-budget-usd 0.50 2>"$err")
rc=$?

RC=$rc RESULT="$result" python3 - <<'PY'
import json, os, sys
try:
    d = json.loads(os.environ["RESULT"])
except ValueError:
    print("검사 실패: 결과가 JSON이 아님")
    sys.exit(2)
out = d.get("structured_output")
if os.environ["RC"] != "0" or d.get("is_error") or out is None:
    print("검사 실패:", d.get("subtype"))
    sys.exit(2)
for f in out["findings"]:
    print(f"{f['file']}:{f['line']}  {f['reason']}")
print("비밀 의심 줄 있음" if out["has_secret"] else "비밀 없음")
sys.exit(1 if out["has_secret"] else 0)
PY
