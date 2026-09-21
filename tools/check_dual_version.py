# -*- coding: utf-8 -*-
"""
双版本源码一致性核对（开发期工具，不参与运行时）
================================================================================

用途
    1.20.1 / 1.21.1 两份源码应当只有「平台 API 差异」，逻辑必须一致。
    本脚本对指定文件做逐行差异统计，并把差异行打印出来人工确认 ——
    防止改了一个版本忘了另一个（历史上出过多次）。

用法
    python tools/check_dual_version.py
    python tools/check_dual_version.py SkillTreeScreen.java
"""

import difflib
import io
import os
import sys

ROOT_1201 = r"f:\IDEA\zifeng_s_custom_skill_tree_1.20.1"
ROOT_1211 = r"f:\IDEA\zifeng_s_custom_skill_tree_1.21.1"

# 需要逐字一致的目录（无平台依赖 -> 必须完全相同）。
# ★ 2026-09-20：这里曾登记 client/search（拼音引擎，纯 JDK 无 MC 依赖）；
#   该包已按用户要求删除（本模组有纯英文玩家，拼音对他们无用）。
#   今后若再新增与平台无关的公共包，记得在此登记才能享受逐字校验。
IDENTICAL_DIRS = []

# 允许存在平台差异的目录（只做差异统计，不判失败）
INSPECT_DIRS = [
    os.path.join("src", "main", "java", "org", "zifeng", "skilltree", "client", "screen"),
]


def read_lines(path):
    with io.open(path, "r", encoding="utf-8") as fh:
        return fh.read().splitlines()


def report_same(name, a, b):
    """逐字一致校验"""
    if a == b:
        print("  [一致] %s (%d 行)" % (name, len(a)))
        return True
    print("  [不同!] %s  -- 该目录要求逐字一致" % name)
    diff = list(difflib.unified_diff(a, b, "1.20.1", "1.21.1", lineterm="", n=1))
    for line in diff[:80]:
        print("      " + line)
    if len(diff) > 80:
        print("      ... 还有 %d 行差异" % (len(diff) - 80))
    return False


def report_diff(name, a, b, quiet):
    """平台可能不同的文件：只统计并列出差异"""
    sm = difflib.SequenceMatcher(None, a, b)
    ratio = sm.ratio()
    diff_lines = []
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == "equal":
            continue
        for line in a[i1:i2]:
            diff_lines.append(("- " + line).rstrip())
        for line in b[j1:j2]:
            diff_lines.append(("+ " + line).rstrip())
    print("  %-34s 相似度 %.4f  差异行 %d" % (name, ratio, len(diff_lines)))
    if not quiet:
        for line in diff_lines[:120]:
            print("      " + line)
        if len(diff_lines) > 120:
            print("      ... 还有 %d 行" % (len(diff_lines) - 120))
    return len(diff_lines)


def main():
    quiet = "--detail" not in sys.argv
    only = [a for a in sys.argv[1:] if not a.startswith("--")]

    ok = True

    print("========== 1. 无平台依赖目录（要求逐字一致） ==========")
    if not IDENTICAL_DIRS:
        print("  （当前未登记任何目录：两份代码均允许存在平台 API 差异）")
    for rel in IDENTICAL_DIRS:
        d1 = os.path.join(ROOT_1201, rel)
        d2 = os.path.join(ROOT_1211, rel)
        if not os.path.isdir(d1):
            print("  跳过（不存在）: %s" % d1)
            continue
        for fn in sorted(os.listdir(d1)):
            if not fn.endswith(".java"):
                continue
            if only and not any(o in fn for o in only):
                continue
            a = read_lines(os.path.join(d1, fn))
            p2 = os.path.join(d2, fn)
            if not os.path.isfile(p2):
                print("  [缺失!] 1.21.1 里没有 %s" % fn)
                ok = False
                continue
            if not report_same(fn, a, read_lines(p2)):
                ok = False

    print()
    print("========== 2. 允许平台差异的目录（仅统计，需人工确认） ==========")
    for rel in INSPECT_DIRS:
        d1 = os.path.join(ROOT_1201, rel)
        d2 = os.path.join(ROOT_1211, rel)
        if not os.path.isdir(d1):
            continue
        for fn in sorted(os.listdir(d1)):
            if not fn.endswith(".java"):
                continue
            if only and not any(o in fn for o in only):
                continue
            p2 = os.path.join(d2, fn)
            if not os.path.isfile(p2):
                print("  [缺失!] 1.21.1 里没有 %s" % fn)
                ok = False
                continue
            report_diff(fn, read_lines(os.path.join(d1, fn)),
                        read_lines(p2), quiet)

    print()
    print("结果: %s" % ("通过" if ok else "有需修问题"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
