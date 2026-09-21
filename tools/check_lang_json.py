# -*- coding: utf-8 -*-
"""
语言文件 JSON 合法性校验（开发期工具，不参与运行时）
================================================================================

为什么必须每次改完语言文件都跑
    某个语言文件只要有<b>一处</b> JSON 语法错误，Minecraft 就会<b>整个文件</b>加载失败，
    表现是「游戏里所有文字都变成 key 的英文原文」—— 看起来像代码 bug，其实是 JSON 坏了。
    最常见的元凶是替换语言文件片段时拼出的重复逗号（",,）。

校验内容
    1. JSON 语法（出错直接给行号列号）
    2. zh_cn / en_us 两侧的 key 集合是否一致（缺翻译会让另一种语言回落成 key）
    3. 两个版本（1.20.1 / 1.21.1）的同一语言 key 集合是否一致

用法
    python tools/check_lang_json.py
"""

import io
import json
import os
import sys

MODES = [
    ("1.20.1-Forge", r"f:\IDEA\zifeng_s_custom_skill_tree_1.20.1"),
    ("1.21.1-NeoForge", r"f:\IDEA\zifeng_s_custom_skill_tree_1.21.1"),
]

LANG_DIR = os.path.join("src", "main", "resources", "assets",
                        "zifeng_s_custom_skill_tree", "lang")

LANGS = ["zh_cn", "en_us", "zh_cn.json.bak_gift", "en_us.json.bak_gift"]


def load(path):
    with io.open(path, "r", encoding="utf-8") as fh:
        return json.load(fh)


def main():
    tables = {}
    failed = False

    print("========== 1. JSON 语法 ==========")
    for name, root in MODES:
        for lang in LANGS:
            path = os.path.join(root, LANG_DIR, lang + ".json") \
                if not lang.endswith(".bak_gift") else \
                os.path.join(root, LANG_DIR, lang)
            if not os.path.isfile(path):
                continue
            try:
                tables[(name, lang)] = load(path)
                print("  [OK]   %-16s %-20s %d 条" % (name, os.path.basename(path),
                                                    len(tables[(name, lang)])))
            except ValueError as ex:
                failed = True
                print("  [坏!]  %-16s %-20s -> %s" % (name, os.path.basename(path), ex))

    if failed:
        print()
        print("!! 有文件 JSON 语法错误。游戏里会表现为「全部显示英文 id」，先修这个。")
        return 1

    print()
    print("========== 2. 同版本内 zh_cn / en_us key 对齐 ==========")
    for name, _root in MODES:
        zh = tables.get((name, "zh_cn"), {})
        en = tables.get((name, "en_us"), {})
        only_zh = sorted(set(zh) - set(en))
        only_en = sorted(set(en) - set(zh))
        if only_zh or only_en:
            failed = True
            print("  [不对称] %s" % name)
            if only_zh:
                print("     只在 zh_cn: %s" % only_zh[:10])
            if only_en:
                print("     只在 en_us: %s" % only_en[:10])
        else:
            print("  [OK]   %-16s 两侧各 %d 条，完全对齐" % (name, len(zh)))

    print()
    print("========== 3. 两版本 key 集合是否一致 ==========")
    for lang in ("zh_cn", "en_us"):
        a = tables.get((MODES[0][0], lang), {})
        b = tables.get((MODES[1][0], lang), {})
        only_a = sorted(set(a) - set(b))
        only_b = sorted(set(b) - set(a))
        if only_a or only_b:
            failed = True
            print("  [不一致] %s" % lang)
            if only_a:
                print("     只在 1.20.1: %s" % only_a[:10])
            if only_b:
                print("     只在 1.21.1: %s" % only_b[:10])
        else:
            print("  [OK]   %-8s 两版本各 %d 条，完全一致" % (lang, len(a)))

    print()
    if failed:
        print("结果: 有问题，请修完再构建")
        return 1
    print("结果: 全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
