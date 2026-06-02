from pathlib import Path
import re
import sys


ROOT = Path("app/src/main/java/com/hc/mixthebluetooth")

ALLOWED_TOP_LEVEL = {
    "api",
    "application",
    "driver",
    "persistence",
    "runtime",
    "ui",
}

OBSOLETE_IMPORT = re.compile(
    r"com\.hc\.mixthebluetooth\."
    r"(activity|fragment|customView|recyclerData|uni|storage|staticdata|remote|impl|local)\b"
)

RULES = [
    (
        "api",
        re.compile(
            r"com\.hc\.mixthebluetooth\."
            r"(activity|fragment|ui|application|remote|impl|local|storage|staticdata|driver\.implementation|persistence\.Encrypted)"
        ),
        "api must expose contracts only",
    ),
    (
        "application",
        re.compile(
            r"com\.hc\.mixthebluetooth\."
            r"(activity|fragment|ui|remote|impl|local|storage|staticdata)"
        ),
        "application must not depend on UI or obsolete packages",
    ),
    (
        "ui",
        re.compile(
            r"com\.hc\.mixthebluetooth\."
            r"(remote|impl|local|storage|staticdata|driver\.implementation\.http|persistence\.Encrypted)"
        ),
        "ui must not depend on remote/http/persistence implementations",
    ),
]

ANDROID_ACTIVITY_IMPORT = re.compile(r"import\s+android\.app\.Activity;")


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def top_package(path: Path) -> str:
    return path.relative_to(ROOT).parts[0]


def main() -> int:
    if not ROOT.exists():
        print(f"source root not found: {ROOT}")
        return 1

    failures = []
    for path in ROOT.rglob("*.java"):
        rel = path.relative_to(ROOT)
        if len(rel.parts) == 1:
            continue
        top = top_package(path)
        text = read_text(path)

        if top not in ALLOWED_TOP_LEVEL:
            failures.append(f"{rel}: obsolete top-level package '{top}'")

        if OBSOLETE_IMPORT.search(text):
            failures.append(f"{rel}: imports obsolete package")

        if "com.hc.mixthebluetooth.staticdata" in text:
            failures.append(f"{rel}: staticdata import is forbidden")

        if top == "api" and ANDROID_ACTIVITY_IMPORT.search(text):
            failures.append(f"{rel}: api must not import android.app.Activity")

        for scope, pattern, message in RULES:
            if top == scope and pattern.search(text):
                failures.append(f"{rel}: {message}")

    if failures:
        print("\n".join(failures))
        return 1

    print("architecture boundaries ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
