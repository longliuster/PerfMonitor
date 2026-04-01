#!/usr/bin/env python3
"""
PerfMonitor - Android 实时性能监控工具
通过 adb 读取手机 FPS、CPU 频率、DDR 频率，在终端实时显示。

用法: python perf_monitor.py
按 Ctrl+C 停止
"""

import subprocess
import time
import os
import sys
import re
import io
import argparse
from collections import defaultdict

# 强制 UTF-8 输出
if os.name == "nt":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

# ─── 配置 ───
INTERVAL = 1.0  # 刷新间隔（秒）

def _find_adb() -> str:
    """Find adb executable"""
    # Try PATH first
    import shutil
    adb_in_path = shutil.which("adb")
    if adb_in_path:
        return adb_in_path
    # Known locations
    known = [
        r"D:\Tool\platform-tools_r33.0.3-windows\platform-tools\adb.exe",
        os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"),
    ]
    for p in known:
        if os.path.isfile(p):
            return p
    return "adb"

ADB = _find_adb()

# ─── 颜色定义（ANSI） ───
class C:
    RESET   = "\033[0m"
    BOLD    = "\033[1m"
    DIM     = "\033[2m"
    RED     = "\033[91m"
    GREEN   = "\033[92m"
    YELLOW  = "\033[93m"
    BLUE    = "\033[94m"
    MAGENTA = "\033[95m"
    CYAN    = "\033[96m"
    WHITE   = "\033[97m"
    BG_DARK = "\033[48;5;234m"


def adb_shell(cmd: str) -> str:
    """执行 adb shell 命令并返回输出"""
    try:
        result = subprocess.run(
            [ADB, "shell", cmd],
            capture_output=True, text=True, timeout=5
        )
        return result.stdout.strip()
    except Exception:
        return ""


def get_cpu_freqs() -> dict:
    """读取所有 CPU 核心的当前频率 (MHz)"""
    output = adb_shell(
        "for i in 0 1 2 3 4 5 6 7; do "
        "cat /sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq 2>/dev/null || echo 0; "
        "done"
    )
    freqs = {}
    for i, line in enumerate(output.splitlines()):
        try:
            khz = int(line.strip())
            freqs[i] = khz // 1000  # KHz -> MHz
        except ValueError:
            freqs[i] = 0
    return freqs


def get_cpu_cluster_info(freqs: dict) -> str:
    """将 CPU 频率按集群分组显示"""
    if not freqs:
        return "N/A"

    # 按频率分组连续核心
    clusters = []
    sorted_cores = sorted(freqs.items())
    if not sorted_cores:
        return "N/A"

    cur_cores = [sorted_cores[0][0]]
    cur_freq = sorted_cores[0][1]

    for core, freq in sorted_cores[1:]:
        if freq == cur_freq:
            cur_cores.append(core)
        else:
            clusters.append((cur_cores, cur_freq))
            cur_cores = [core]
            cur_freq = freq
    clusters.append((cur_cores, cur_freq))

    parts = []
    for cores, freq in clusters:
        if len(cores) == 1:
            label = f"C{cores[0]}"
        else:
            label = f"C{cores[0]}-{cores[-1]}"
        parts.append(f"{label}:{freq}MHz")

    return " | ".join(parts)


def get_ddr_freq() -> str:
    """读取 DDR 频率"""
    # 尝试直接读取
    output = adb_shell("cat /sys/class/devfreq/ddr_devfreq/cur_freq 2>/dev/null")
    if output and "denied" not in output.lower():
        try:
            hz = int(output)
            if hz > 1_000_000_000:
                return f"{hz // 1_000_000}MHz"
            elif hz > 1_000_000:
                return f"{hz // 1_000}MHz"
            else:
                return f"{hz}MHz"
        except ValueError:
            pass

    # 尝试通过 dumpsys 获取
    output = adb_shell("dumpsys meminfo 2>/dev/null | grep -i 'ddr\\|memory.*freq' | head -3")
    if output:
        return output.split('\n')[0].strip()[:30]

    return "N/A (need root)"


# ─── FPS 计算 ───
class FpsCalculator:
    """通过 SurfaceFlinger framestats 计算 FPS"""

    def __init__(self):
        self.last_total_frames = {}
        self.last_time = time.time()

    def get_fps(self) -> tuple:
        """返回 (当前窗口名, fps)"""
        # 获取当前前台 Activity
        focus_output = adb_shell(
            "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | head -2"
        )

        window_name = "unknown"
        for line in focus_output.splitlines():
            # 匹配 mCurrentFocus=Window{xxx u0 com.xxx/com.xxx.Activity}
            m = re.search(r'(\S+/\S+)\}', line)
            if m:
                window_name = m.group(1)
                break
            # 匹配包名
            m = re.search(r'(com\.\S+)', line)
            if m:
                window_name = m.group(1).rstrip('}')
                break

        # 通过 gfxinfo 获取总帧数
        pkg = window_name.split('/')[0] if '/' in window_name else window_name
        output = adb_shell(f"dumpsys gfxinfo {pkg} 2>/dev/null | head -10")

        total_frames = 0
        for line in output.splitlines():
            if "Total frames rendered" in line:
                m = re.search(r'(\d+)', line)
                if m:
                    total_frames = int(m.group(1))
                break

        now = time.time()
        dt = now - self.last_time
        fps = 0

        if pkg in self.last_total_frames and dt > 0:
            delta = total_frames - self.last_total_frames[pkg]
            if delta >= 0:
                fps = int(delta / dt)

        self.last_total_frames[pkg] = total_frames
        self.last_time = now

        return window_name, min(fps, 120)  # cap at 120


def get_gpu_freq() -> str:
    """读取 GPU 频率"""
    output = adb_shell("cat /sys/class/devfreq/gpufreq/cur_freq 2>/dev/null")
    if output and "denied" not in output.lower():
        try:
            hz = int(output)
            if hz > 1_000_000_000:
                return f"{hz // 1_000_000}MHz"
            elif hz > 1_000_000:
                return f"{hz // 1_000}MHz"
            else:
                return f"{hz}MHz"
        except ValueError:
            pass
    return "N/A"


def clear_screen():
    os.system("cls" if os.name == "nt" else "clear")


def print_header():
    print(f"{C.CYAN}{C.BOLD}+------------------------------------------------------+{C.RESET}")
    print(f"{C.CYAN}{C.BOLD}|          [*] PerfMonitor - Real-time Monitor         |{C.RESET}")
    print(f"{C.CYAN}{C.BOLD}+------------------------------------------------------+{C.RESET}")
    print()


def fps_color(fps: int) -> str:
    if fps >= 55:
        return C.GREEN
    elif fps >= 30:
        return C.YELLOW
    else:
        return C.RED


def freq_bar(freq_mhz: int, max_mhz: int = 3000) -> str:
    """生成频率条形图"""
    bar_len = 20
    filled = min(int(freq_mhz / max_mhz * bar_len), bar_len)
    bar = "#" * filled + "-" * (bar_len - filled)

    if freq_mhz > max_mhz * 0.7:
        color = C.RED
    elif freq_mhz > max_mhz * 0.4:
        color = C.YELLOW
    else:
        color = C.GREEN

    return f"{color}{bar}{C.RESET}"


def main():
    parser = argparse.ArgumentParser(description="Android PerfMonitor")
    parser.add_argument("--count", "-n", type=int, default=0,
                        help="Number of iterations (0=infinite)")
    parser.add_argument("--interval", "-i", type=float, default=1.0,
                        help="Refresh interval in seconds")
    args = parser.parse_args()

    interval = args.interval

    # 启用 Windows ANSI 支持
    if os.name == "nt":
        os.system("")  # Enable ANSI on Windows

    # 检查设备连接
    result = subprocess.run([ADB, "devices"], capture_output=True, text=True, timeout=5)
    devices = [l for l in result.stdout.splitlines() if "\tdevice" in l]
    if not devices:
        print(f"{C.RED}Error: No Android device found, check adb connection{C.RESET}")
        sys.exit(1)

    device_id = devices[0].split("\t")[0]

    # 获取设备信息
    model = adb_shell("getprop ro.product.model")
    android_ver = adb_shell("getprop ro.build.version.release")
    soc = adb_shell("getprop ro.soc.model") or adb_shell("getprop ro.board.platform")

    fps_calc = FpsCalculator()

    print(f"{C.CYAN}Connecting to {device_id}...{C.RESET}")
    time.sleep(1)

    iteration = 0
    try:
        while True:
            clear_screen()
            print_header()

            # 设备信息
            print(f"  {C.DIM}Device: {model} | Android {android_ver} | SoC: {soc}{C.RESET}")
            print(f"  {C.DIM}ID: {device_id} | Interval: {INTERVAL}s{C.RESET}")
            print()

            # FPS
            window, fps = fps_calc.get_fps()
            fc = fps_color(fps)
            print(f"  {C.BOLD}[FPS]{C.RESET}")
            print(f"     {fc}{C.BOLD}{fps:3d} fps{C.RESET}  {C.DIM}({window}){C.RESET}")
            print()

            # CPU 频率
            cpu_freqs = get_cpu_freqs()
            cluster_info = get_cpu_cluster_info(cpu_freqs)
            max_freq = max(cpu_freqs.values()) if cpu_freqs else 0

            print(f"  {C.BOLD}[CPU]{C.RESET}  {C.DIM}(max: {max_freq}MHz){C.RESET}")
            for core, freq in sorted(cpu_freqs.items()):
                bar = freq_bar(freq)
                print(f"     CPU{core}: {bar} {C.YELLOW}{freq:5d}{C.RESET} MHz")
            print(f"     {C.DIM}Cluster: {cluster_info}{C.RESET}")
            print()

            # GPU 频率
            gpu = get_gpu_freq()
            print(f"  {C.BOLD}[GPU]{C.RESET}")
            print(f"     {C.MAGENTA}{gpu}{C.RESET}")
            print()

            # DDR 频率
            ddr = get_ddr_freq()
            print(f"  {C.BOLD}[DDR]{C.RESET}")
            print(f"     {C.CYAN}{ddr}{C.RESET}")
            print()

            print(f"  {C.DIM}Press Ctrl+C to stop{C.RESET}")

            iteration += 1
            if args.count > 0 and iteration >= args.count:
                print(f"\n{C.CYAN}Completed {iteration} iterations.{C.RESET}")
                break

            time.sleep(interval)

    except KeyboardInterrupt:
        print(f"\n{C.CYAN}Monitor stopped.{C.RESET}")


if __name__ == "__main__":
    main()
