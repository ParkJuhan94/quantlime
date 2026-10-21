#!/usr/bin/env python3
"""린트/컴파일 경고 로그를 요약해 GitHub Actions 실행 화면과 PR 변경 줄에 보여준다.

사용법: warning_report.py <javac|oxlint> <로그 파일> <제목> [--root 작업 디렉터리] [--only 규칙,...] [--except 규칙,...]

- 요약표(규칙별 개수, 파일별 상위 10개)를 $GITHUB_STEP_SUMMARY에 쓴다. 없으면 표준 출력.
- PR 실행이면 "PR이 바꾼 파일"의 경고만 ::warning 어노테이션으로 낸다(Files changed에 표시).
  Actions는 스텝당 어노테이션을 10개까지만 보여주므로 상한을 두고, 나머지는 요약표로 본다.
- --only/--except로 규칙을 골라 한 로그를 여러 리포트(예: NullAway와 Error Prone)로 나눠 낼 수 있다.
  어노테이션 10개 상한이 스텝마다 따로 적용되므로 나누면 각 리포트가 자기 몫을 갖는다.
- 경고가 있어도 종료 코드는 0이다 - 보기 위한 도구이지 게이트가 아니다.
"""
import collections
import os
import re
import subprocess
import sys

JAVAC = re.compile(r"^(?P<file>/\S+?\.java):(?P<line>\d+): warning: \[(?P<rule>\w+)\] (?P<msg>.*)$")
# --format unix 출력: "파일:줄:열: 메시지 [Warning/규칙]". oxlint는 CI 환경에서 기본 형식이 github로 바뀌어
# 로그에서 파싱할 수 없으므로 워크플로에서 --format unix를 명시한다(2026-10-12, 요약이 0개로 찍히던 문제)
OXLINT = re.compile(r"^(?P<file>\S+?):(?P<line>\d+):(?P<col>\d+): (?P<msg>.*) \[Warning/(?P<rule>[^\]]+)\]$")
ANNOTATION_LIMIT = 10


def parse(kind, path, root):
    pattern = JAVAC if kind == "javac" else OXLINT
    seen = set()
    items = []
    with open(path, encoding="utf-8", errors="replace") as f:
        for raw in f:
            line = re.sub(r"^\S+\t\S.*?\t\S+Z ", "", raw.rstrip("\n"))  # gh run log 접두사
            line = re.sub(r"^\d{4}-\d\d-\d\dT[\d:.]+Z ", "", line)  # Actions 타임스탬프
            m = pattern.match(line)
            if not m:
                continue
            file = m["file"]
            if root and file.startswith(root):
                file = os.path.relpath(file, root)
            key = (file, m["line"], m["rule"], m["msg"])
            if key in seen:  # 같은 소스가 여러 컴파일 단계에서 중복 출력되는 경우
                continue
            seen.add(key)
            items.append({"file": file, "line": int(m["line"]), "rule": m["rule"], "msg": m["msg"]})
    return items


def changed_files():
    """PR 실행일 때만 변경 파일 집합을 돌려준다(아니면 None)."""
    if os.environ.get("GITHUB_EVENT_NAME") != "pull_request":
        return None
    repo, ref = os.environ.get("GITHUB_REPOSITORY"), os.environ.get("GITHUB_REF", "")
    m = re.match(r"refs/pull/(\d+)/merge", ref)
    if not (repo and m):
        return None
    out = subprocess.run(
        ["gh", "api", f"repos/{repo}/pulls/{m[1]}/files", "--paginate", "-q", ".[].filename"],
        capture_output=True, text=True,
    )
    return set(out.stdout.split()) if out.returncode == 0 else None


def main():
    args = sys.argv[1:]
    root = ""
    if "--root" in args:
        i = args.index("--root")
        root = args[i + 1].rstrip("/") + "/"
        del args[i : i + 2]
    only, exclude = set(), set()
    for flag, target in (("--only", only), ("--except", exclude)):
        if flag in args:
            i = args.index(flag)
            target.update(r for r in args[i + 1].split(",") if r)
            del args[i : i + 2]
    kind, log, title = args[0], args[1], args[2]
    items = parse(kind, log, root)
    if only:
        items = [i for i in items if i["rule"] in only]
    if exclude:
        items = [i for i in items if i["rule"] not in exclude]

    by_rule = collections.Counter(i["rule"] for i in items)
    by_file = collections.Counter(i["file"] for i in items)
    lines = [f"### {title}: 경고 {len(items)}개", ""]
    if items:
        lines += ["| 규칙 | 개수 |", "|---|---:|"] + [f"| `{r}` | {c} |" for r, c in by_rule.most_common()]
        lines += ["", "| 파일 (상위 10) | 개수 |", "|---|---:|"]
        lines += [f"| `{f}` | {c} |" for f, c in by_file.most_common(10)]
    else:
        lines.append("경고 없음")
    summary = "\n".join(lines) + "\n\n"

    out = os.environ.get("GITHUB_STEP_SUMMARY")
    if out:
        with open(out, "a", encoding="utf-8") as f:
            f.write(summary)
    else:
        print(summary)

    changed = changed_files()
    if changed is not None:
        mine = [i for i in items if i["file"] in changed]
        for i in mine[:ANNOTATION_LIMIT]:
            msg = i["msg"].replace("%", "%25").replace("\r", "").replace("\n", " ")
            print(f"::warning file={i['file']},line={i['line']},title={i['rule']}::{msg}")
        print(f"PR 변경 파일의 경고 {len(mine)}개 중 {min(len(mine), ANNOTATION_LIMIT)}개를 어노테이션으로 표시")


if __name__ == "__main__":
    main()
