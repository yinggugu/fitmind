import re
from typing import Dict, List


FOOD_ALIASES: Dict[str, str] = {
    "鸡胸": "鸡胸肉",
    "煎鸡胸": "鸡胸肉",
    "煎鸡胸肉": "鸡胸肉",
    "烤鸡胸": "鸡胸肉",
    "烤鸡胸肉": "鸡胸肉",
    "白米饭": "米饭",
    "西红柿": "番茄",
    "纯黄豆豆浆": "黄豆豆浆",
}

SEPARATOR_PATTERN = re.compile(r"\s*[,，、+＋➕]\s*")
MULTI_SPACE_PATTERN = re.compile(r"[\s\u3000]+")


def clean_text(value: object) -> str:
    if value is None:
        return ""
    return MULTI_SPACE_PATTERN.sub(" ", str(value)).strip()


def normalize_food_name(value: object) -> str:
    original = clean_text(value)
    if not original:
        return ""
    parts = [part.strip() for part in SEPARATOR_PATTERN.split(original) if part.strip()]
    if len(parts) == 1:
        return FOOD_ALIASES.get(parts[0], parts[0])
    normalized: List[str] = [FOOD_ALIASES.get(part, part) for part in parts]
    return "、".join(normalized)


def normalization_changed(value: object) -> bool:
    return clean_text(value) != normalize_food_name(value)


def likely_undelimited_compound(value: object) -> bool:
    text = clean_text(value)
    if not text or SEPARATOR_PATTERN.search(text):
        return False
    if text in ("牛肉午餐肉", "高蛋白牛肉午餐肉"):
        return False
    markers = (
        "米饭", "地瓜", "红薯", "玉米", "鸡蛋", "水煮蛋", "午餐肉", "牛肉",
        "鸡腿", "豆浆", "空心菜", "青菜", "南瓜", "汉堡", "米线", "丸子",
    )
    return sum(1 for marker in markers if marker in text) >= 2
