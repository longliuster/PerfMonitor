"""Quick test - read device perf data once and print"""
import subprocess

ADB = "adb"

def adb_shell(cmd):
    try:
        r = subprocess.run([ADB, "shell", cmd], capture_output=True, text=True, timeout=5)
        return r.stdout.strip()
    except:
        return ""

print("=== Device ===")
print("Model:", adb_shell("getprop ro.product.model"))
print("Android:", adb_shell("getprop ro.build.version.release"))
print("SoC:", adb_shell("getprop ro.soc.model"))

print("\n=== CPU Freq (MHz) ===")
for i in range(8):
    khz = adb_shell(f"cat /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_cur_freq")
    try:
        print(f"  CPU{i}: {int(khz)//1000} MHz")
    except:
        print(f"  CPU{i}: N/A")

print("\n=== GPU Freq ===")
gpu = adb_shell("cat /sys/class/devfreq/gpufreq/cur_freq")
print(f"  GPU: {gpu}")

print("\n=== DDR Freq ===")
ddr = adb_shell("cat /sys/class/devfreq/ddr_devfreq/cur_freq")
print(f"  DDR: {ddr if ddr else 'Permission denied (need root)'}")

print("\n=== FPS (gfxinfo) ===")
focus = adb_shell("dumpsys window | grep mCurrentFocus")
print(f"  Focus: {focus}")
import re
m = re.search(r'(\S+/\S+)\}', focus)
pkg = m.group(1).split('/')[0] if m else "com.miui.home"
gfx = adb_shell(f"dumpsys gfxinfo {pkg} | grep 'Total frames'")
print(f"  {gfx}")

print("\nDone.")
