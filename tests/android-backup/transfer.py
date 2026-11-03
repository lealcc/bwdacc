"""Move only disposable synthetic backup fixtures between isolated emulator UIDs."""
import pathlib
import subprocess
import sys
import uuid

adb, serial, folder = sys.argv[1:]
if subprocess.check_output([adb, "-s", serial, "shell", "getprop", "ro.kernel.qemu"]).strip() != b"1":
    raise SystemExit("Emulator required")
stage = pathlib.Path(folder)
source, target = "com.leal.bwdacc.backupsource", "com.leal.bwdacc.backuptarget"
for name, dest in (("output.bwdvault", "input.bwdvault"), ("code.txt", "code.txt"), ("source-image.sha256", "source-image.sha256")):
    local = stage / name
    local.write_bytes(subprocess.check_output([adb, "-s", serial, "exec-out", "run-as", source, "cat", "cache/" + name]))
    remote = "/data/local/tmp/bwdacc-test-" + uuid.uuid4().hex
    subprocess.run([adb, "-s", serial, "push", str(local), remote], check=True, stdout=subprocess.DEVNULL)
    try:
        subprocess.run([adb, "-s", serial, "shell", "run-as", target, "cp", remote, "cache/" + dest], check=True)
    finally:
        subprocess.run([adb, "-s", serial, "shell", "rm", remote], check=True)
