#!/usr/bin/env python3
"""ドキュメントの不変条件を検査する。

検査するのは 3 つ。

  1. 文書間リンクの参照先が実在するか
  2. リンクのアンカー（#見出し）が実在するか
  3. バッククォートで囲まれた `XxxTest` が実在するテストクラスを指しているか
     （**クラスの実在だけ**。その振る舞いを検証するテストケースがあるかは見ない）

手で確認していたものだけを機械化している。**検査できないドキュメント規約はここに足さない。**
守られたか分からないルールは、実質存在しないのと同じ。

検査が成立しなかったこと自体も失敗にする。黙って no-op になると
「通った」と「検査していない」の区別が付かなくなるため。

使い方: ./tools/check_docs.py
"""

from __future__ import annotations

import re
import subprocess
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
TEST_DIR = ROOT / "app/src/test/java/jp/co/zaico/codingtest"

# 凍結済みの変更 spec（docs/specs/NNN-*.md）は検査しない。
# あれはマージ時点のスナップショットで、以後は書き換えない決まりになっている。
# spec.md の見出しやテストクラス名が変われば当然リンクは古くなるが、
# **直すことが規約で禁じられているものを「問題」として報告し続けても直せない。**
# 執筆中のリンク切れは、その PR のレビューで拾う。
FROZEN = re.compile(r"^docs/specs/\d{3}-")

# ``` で囲まれた中身は文書ではなくコード例。見出しやリンクとして数えない。
# 手順書はコマンド例やテンプレートを大量に含むので、除かないと
# **幻の見出しがアンカーとして登録され、リンク切れを見逃す**。
FENCE = re.compile(r"^(?P<f>```|~~~).*?^(?P=f)", re.M | re.S)

LINK = re.compile(r"\[[^\]]*\]\(([^)#]*)(?:#([^)]*))?\)")
HEADING = re.compile(r"^#+\s+(.+)$", re.M)
TEST_REF = re.compile(r"`([A-Z][A-Za-z0-9]*Test)`")


def tracked_markdown() -> list[Path]:
    """git が追跡している .md をすべて検査対象にする。

    列挙をハードコードすると、増やした文書が黙って検査外になる。
    """
    out = subprocess.run(
        ["git", "ls-files", "-z", "*.md"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    return sorted(
        ROOT / p for p in out.split("\0") if p and not FROZEN.match(p)
    )


def slug(heading: str) -> str:
    """GitHub（github-slugger）の見出しアンカーを再現する。

    ASCII の記号だけを除くと全角の約物（（）・、「」など）が残り、
    **正しいアンカーを「不明」と報告する**。Unicode のカテゴリで判定する。
    """
    kept = [c for c in heading if c in "-_" or unicodedata.category(c)[0] not in "PS"]
    return "".join(kept).strip().lower().replace(" ", "-")


def anchors_of(text: str) -> set[str]:
    """同名の見出しには GitHub と同じく -1, -2 … が付く。"""
    seen: defaultdict[str, int] = defaultdict(int)
    result = set()
    for heading in HEADING.findall(text):
        base = slug(heading)
        n = seen[base]
        seen[base] += 1
        result.add(base if n == 0 else f"{base}-{n}")
    return result


def main() -> int:
    problems: list[str] = []
    unchecked: list[str] = []
    docs = tracked_markdown()
    if not docs:
        print("  検査対象の .md が 1 件も見つからない")
        return 1

    texts = {p: FENCE.sub("", p.read_text(encoding="utf-8")) for p in docs}
    anchors = {p: anchors_of(t) for p, t in texts.items()}

    for path, text in texts.items():
        rel = path.relative_to(ROOT)
        for target, anchor in LINK.findall(text):
            if target.startswith(("http://", "https://", "mailto:")):
                continue

            if not target:
                resolved = path
            elif target.startswith("/"):
                resolved = (ROOT / target.lstrip("/")).resolve()
            else:
                resolved = (path.parent / target).resolve()

            if not resolved.exists():
                problems.append(f"{rel}: リンク切れ → {target}")
                continue

            if not anchor:
                continue
            if resolved not in anchors:
                # 凍結済みの spec や追跡外の .md。見出しを読まないので検証できない。
                # 黙って通すと「検査した」と区別が付かなくなるため、数えて報告する。
                unchecked.append(f"{rel}: アンカー未検証（対象外への参照） → {target}#{anchor}")
            elif unquote(anchor) not in anchors[resolved]:
                problems.append(f"{rel}: アンカー不明 → {target or '.'}#{anchor}")

    if not TEST_DIR.is_dir():
        problems.append(
            f"テストの置き場が見つからない → {TEST_DIR.relative_to(ROOT)}"
            "（移動したならこのスクリプトの TEST_DIR を直す）",
        )
    else:
        existing = {p.stem for p in TEST_DIR.rglob("*.kt")}
        for path, text in texts.items():
            rel = path.relative_to(ROOT)
            for name in sorted(set(TEST_REF.findall(text))):
                if name not in existing:
                    problems.append(f"{rel}: 存在しないテストクラスを参照 → {name}")

    for p in problems:
        print(f"  {p}")
    for u in unchecked:
        print(f"  - {u}")
    print(
        f"検査した文書: {len(docs)} 件 / 問題: {len(problems)} 件"
        + (f" / 未検証: {len(unchecked)} 件" if unchecked else ""),
    )
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
