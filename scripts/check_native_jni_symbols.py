#!/usr/bin/env python3
"""校验 libfqapi_core.so 导出了 Kotlin 侧声明的 JNI 入口。

Kotlin 的 `external fun` 在运行时按名字查找符号，漏一个就是
`UnsatisfiedLinkError`，而且只在真机上炸。这个检查把那个失败提前到构建阶段。

用法：
    python scripts/check_native_jni_symbols.py [--so <path>] [--nm <llvm-nm>]
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

# Kotlin 包 com.fqapp.native.core.FqCoreBridge 的方法名 -> JNI 符号。
REQUIRED_SYMBOLS = [
    "Java_com_fqapp_native_core_FqCoreBridge_nativeVersion",
    "Java_com_fqapp_native_core_FqCoreBridge_nativeStart",
    "Java_com_fqapp_native_core_FqCoreBridge_nativeStop",
    "Java_com_fqapp_native_core_FqCoreBridge_nativeCancel",
]

DEFAULT_SO = "rust/target/aarch64-linux-android/release/libfqapi_core.so"


def find_nm(explicit: str | None) -> str:
    if explicit:
        return explicit
    ndk = os.environ.get("ANDROID_NDK_HOME") or os.environ.get("ANDROID_NDK_ROOT")
    if ndk:
        host = "windows-x86_64" if os.name == "nt" else sys.platform
        candidate = Path(ndk) / "toolchains" / "llvm" / "prebuilt" / host / "bin" / "llvm-nm.exe"
        if candidate.exists():
            return str(candidate)
        candidate_no_ext = candidate.with_suffix("")
        if candidate_no_ext.exists():
            return str(candidate_no_ext)
    for name in ("llvm-nm", "llvm-nm.exe", "nm"):
        found = shutil.which(name)
        if found:
            return found
    raise SystemExit("找不到 llvm-nm；设置 ANDROID_NDK_HOME 或用 --nm 指定")


def exported_symbols(so: Path, nm: str) -> set[str]:
    out = subprocess.run(
        [nm, "-D", "--defined-only", str(so)],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    symbols = set()
    for line in out.splitlines():
        parts = line.split()
        if parts:
            symbols.add(parts[-1])
    return symbols


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--so", default=DEFAULT_SO)
    parser.add_argument("--nm")
    args = parser.parse_args(argv)

    so = Path(args.so)
    if not so.is_file():
        print(f"错误：找不到 {so}，先跑 cross-compile。", file=sys.stderr)
        return 2

    nm = find_nm(args.nm)
    symbols = exported_symbols(so, nm)
    missing = [symbol for symbol in REQUIRED_SYMBOLS if symbol not in symbols]
    if missing:
        print(f"错误：{so} 缺少 {len(missing)} 个 JNI 符号：", file=sys.stderr)
        for symbol in missing:
            print(f"  - {symbol}", file=sys.stderr)
        return 1

    print(f"ok: {len(REQUIRED_SYMBOLS)} 个 JNI 符号齐全（{so}）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())