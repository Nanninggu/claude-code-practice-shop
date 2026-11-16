# PreToolUse 훅: 클로드가 실행하려는 셸 명령을 검사한다. 입력은 표준입력의 JSON이다.
import json
import re
import sys

command = json.load(sys.stdin)["tool_input"]["command"]

RULES = [
    (r"git\s+reset\s+--hard", "커밋하지 않은 변경이 사라집니다. 되돌릴 때는 git stash로 따로 치워 두세요."),
    (r"git\s+push\b.*(--force|\s-f\b)", "강제 푸시는 원격 이력을 덮어씁니다. 사람이 직접 하세요."),
    (r"rm\s+-\w*r\w*f|rm\s+-\w*f\w*r", "rm -rf는 막혀 있습니다. 지울 파일을 하나씩 알려 주세요."),
]

for pattern, reason in RULES:
    if re.search(pattern, command):
        print("차단: " + reason, file=sys.stderr)
        sys.exit(2)
