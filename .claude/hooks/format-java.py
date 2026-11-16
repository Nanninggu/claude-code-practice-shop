# PostToolUse 훅: 클로드가 고친 자바 파일의 형식을 바로잡는다. 입력은 표준입력의 JSON이다.
import json
import sys

path = json.load(sys.stdin)["tool_input"]["file_path"]
if not path.endswith(".java"):
    sys.exit(0)

before = open(path, encoding="utf-8").read()
lines = [line.expandtabs(4).rstrip() for line in before.split("\n")]
after = "\n".join(lines).rstrip("\n") + "\n"

if after != before:
    open(path, "w", encoding="utf-8").write(after)
    name = path.split("/")[-1]
    print(json.dumps({"systemMessage": "형식 정리: " + name}, ensure_ascii=False))
