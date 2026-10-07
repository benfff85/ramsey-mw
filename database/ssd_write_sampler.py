"""Attribute SSD writes over a window: SMART (device truth), macOS per-process disk I/O
(proc_pid_rusage), Docker per-container block I/O, and MySQL InnoDB counters.
Usage: ssd_write_sampler.py SECONDS        (needs: brew install smartmontools)
"""
import ctypes, re, subprocess, sys, time

libproc = ctypes.CDLL("/usr/lib/libproc.dylib")


class RusageInfoV2(ctypes.Structure):
    _fields_ = [("ri_uuid", ctypes.c_uint8 * 16)] + [(n, ctypes.c_uint64) for n in (
        "user_time", "system_time", "pkg_idle_wkups", "interrupt_wkups", "pageins", "wired_size",
        "resident_size", "phys_footprint", "proc_start_abstime", "proc_exit_abstime",
        "child_user_time", "child_system_time", "child_pkg_idle_wkups", "child_interrupt_wkups",
        "child_pageins", "child_elapsed_abstime", "diskio_bytesread", "diskio_byteswritten")]


def processes():
    out = subprocess.run(["ps", "-axo", "pid=,comm="], capture_output=True, text=True).stdout
    procs = {}
    for line in out.splitlines():
        pid, _, comm = line.strip().partition(" ")
        info = RusageInfoV2()
        if libproc.proc_pid_rusage(int(pid), 2, ctypes.byref(info)) == 0:
            procs[int(pid)] = (comm.strip(), info.diskio_byteswritten)
    return procs


def smart():
    out = subprocess.run(["smartctl", "-A", "disk0"], capture_output=True, text=True).stdout
    return int(re.search(r"Data Units Written:\s+([\d,]+)", out)[1].replace(",", "")) * 512_000


UNITS = {"B": 1, "kB": 1e3, "KB": 1e3, "MB": 1e6, "GB": 1e9, "TB": 1e12, "KiB": 1024, "MiB": 1024**2, "GiB": 1024**3}


def to_bytes(s):
    m = re.match(r"([\d.]+)\s*([A-Za-z]+)", s.strip())
    return float(m[1]) * UNITS[m[2]] if m else 0.0


def docker_blockio():
    out = subprocess.run(["docker", "stats", "--no-stream", "--format", "{{.Name}}\t{{.BlockIO}}"],
                         capture_output=True, text=True).stdout
    return {l.split("\t")[0]: to_bytes(l.split("\t")[1].split("/")[1]) for l in out.splitlines() if "\t" in l}


def mysql():
    out = subprocess.run(["docker", "exec", "ramsey-db-mysql", "sh", "-c",
                          'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "show global status where Variable_name in '
                          '(\\"Innodb_data_written\\",\\"Innodb_os_log_written\\")"'],
                         capture_output=True, text=True).stdout
    return {l.split("\t")[0]: int(l.split("\t")[1]) for l in out.splitlines() if "\t" in l}


def snap():
    return {"t": time.time(), "smart": smart(), "procs": processes(), "docker": docker_blockio(), "mysql": mysql()}


a = snap(); time.sleep(int(sys.argv[1])); b = snap()
day = 86400 / (b["t"] - a["t"])
print(f"window {b['t'] - a['t']:.0f}s")
print(f"SMART device writes: {(b['smart'] - a['smart']) * day / 1e9:.1f} GB/day")
rows = sorted(((w - a["procs"][p][1], c) for p, (c, w) in b["procs"].items()
               if p in a["procs"] and a["procs"][p][0] == c and w > a["procs"][p][1]), reverse=True)
for dw, c in rows[:8]:
    print(f"  {dw * day / 1e9:8.2f} GB/day  {c[-60:]}")
for n in sorted(b["docker"]):
    dw = b["docker"][n] - a["docker"].get(n, 0)
    if dw > 1e6:
        print(f"  {dw * day / 1e9:8.2f} GB/day  container {n}")
m = {k: (b["mysql"][k] - a["mysql"][k]) * day / 1e9 for k in b["mysql"]}
print(f"MySQL InnoDB: data {m.get('Innodb_data_written', 0):.1f} GB/day, redo {m.get('Innodb_os_log_written', 0):.1f} GB/day")
