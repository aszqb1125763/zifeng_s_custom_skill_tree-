# -*- coding: utf-8 -*-
"""繁体中文语言文件生成器（开发期工具，不参与运行时）
================================================================================

用途
    由 `zh_cn.json` 生成繁体中文语言文件，避免人工维护多份几乎相同的长文本。
    ⚠️ 必须同时生成 `zh_tw` 与 `zh_hk` 两份 —— Minecraft 的繁体中文是
    <b>两个独立语言</b>（台灣 zh_tw / 香港 zh_hk），缺哪个就会让选了那个语言的玩家
    看到**整个模组回落成英文**（原版界面仍是中文，只有本模组是英文，很容易误判成代码 bug）。

    2026-10-11 实测踩坑：只生成了 zh_tw，玩家语言是 zh_hk →
    模组的 837 个键全部回落到 en_us，技能树整片英文。

流程
    1. 先把「简体用词 → 繁中官方译名」的小表替换掉（TERM_FIXES）
       —— 必须在简繁转换<b>之前</b>做：这些词转换后字形不变，之后再改会漏掉；
    2. 再用 OpenCC 整体转换（zh_tw 用 s2twp（含台灣用語轉換）、zh_hk 用 s2hk）；
    3. 最后修 s2twp / s2hk 的过度转换（POST_FIXES）；
    4. 写出语言文件，键顺序与缩进与 `zh_cn.json` 保持一致，便于 diff。

⚠️ 术语小表只收「与 Minecraft 官方繁体译名明显不同」且不会误伤的词。
    刻意<b>不</b>收「箱子 → 儲物箱」这类：本模组文案里「箱子」多用于泛称，
    硬换会在「打开过的箱子」这种句子里变得生硬。

用法
    python -m pip install opencc-python-reimplemented
    python tools/gen_zh_hant.py
"""

import io
import json
import os

try:
    from opencc import OpenCC
except ImportError:  # pragma: no cover
    raise SystemExit("需要 opencc：python -m pip install opencc-python-reimplemented")

LANG_REL = os.path.join("src", "main", "resources", "assets",
                        "zifeng_s_custom_skill_tree", "lang")

PROJECTS = (
    r"F:\IDEA\zifeng_s_custom_skill_tree_1.20.1",
    r"F:\IDEA\zifeng_s_custom_skill_tree_1.21.1",
)

# 目标语言 → OpenCC 配置
#   s2twp = 簡→繁（台灣正體 + 臺灣用語）
#   s2hk  = 簡→繁（香港特別行政區字形）
TARGETS = (
    ("zh_tw", "s2twp"),
    ("zh_hk", "s2hk"),
)

# 简体内出现、但必须换成繁中官方译名再转换的词
TERM_FIXES = {
    "末影": "終界",
    "下界": "地獄",
    "凋灵": "凋零",
    "刷怪笼": "生怪磚",
}

# 过度转换修正（在转换<b>之后</b>应用）
#   OpenCC 的用語轉換按「软件文档」语境换词，落到游戏文案上会读起来很怪：
#     「伤害类型」 → 傷害型別（应为 類型）
#     「绑定容器」 → 繫結容器（应为 綁定）
POST_FIXES = {
    "型別": "類型",
    "繫結": "綁定",
}


def load(path):
    with io.open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def convert_name(name, converter):
    """单个文案：先术语修正，再整句简繁转换，最后修过度转换。"""
    for simplified, traditional in TERM_FIXES.items():
        name = name.replace(simplified, traditional)
    name = converter.convert(name)
    for bad, good in POST_FIXES.items():
        name = name.replace(bad, good)
    return name


def main():
    source_path = os.path.join(PROJECTS[0], LANG_REL, "zh_cn.json")
    source = load(source_path)
    reference_en = load(os.path.join(PROJECTS[0], LANG_REL, "en_us.json"))
    print("zh_cn 键数: %d | en_us 键数: %d" % (len(source), len(reference_en)))
    if set(source) != set(reference_en):
        raise SystemExit("zh_cn 与 en_us 键集合不一致，先跑 check_lang_json.py")

    for lang, config in TARGETS:
        converter = OpenCC(config)
        converted = {}
        for key, value in source.items():
            converted[key] = convert_name(value, converter) if isinstance(value, str) else value

        for root in PROJECTS:
            target = os.path.join(root, LANG_REL, lang + ".json")
            with io.open(target, "w", encoding="utf-8", newline="\n") as handle:
                json.dump(converted, handle, ensure_ascii=False, indent=2)
                handle.write("\n")

        bad = [k for k, v in converted.items() if isinstance(v, str) and "?" in v]
        residue = [k for k, v in converted.items()
                   if isinstance(v, str) and any(b in v for b in POST_FIXES)]
        changed = sum(1 for k in source if source[k] != converted[k])
        print("  %-6s (%-6s) 写出 2 份 | 与 zh_cn 不同 %d/%d | 含'?' %d | 残留过度转换 %d"
              % (lang, config, changed, len(source), len(bad), len(residue)))


if __name__ == "__main__":
    main()
