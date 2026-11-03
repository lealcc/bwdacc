using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

namespace Bwdacc.Desktop;

public sealed class ProcessJob : IDisposable
{
    private readonly SafeFileHandle handle;
    [StructLayout(LayoutKind.Sequential)] private struct Basic { public long ProcessTime, JobTime; public uint Flags; public UIntPtr MinWorking, MaxWorking; public uint ActiveLimit; public UIntPtr Affinity; public uint Priority, Scheduling; }
    [StructLayout(LayoutKind.Sequential)] private struct Counters { public ulong ReadOps, WriteOps, OtherOps, ReadBytes, WriteBytes, OtherBytes; }
    [StructLayout(LayoutKind.Sequential)] private struct Limits { public Basic Basic; public Counters Counters; public UIntPtr ProcessMemory, JobMemory, PeakProcess, PeakJob; }
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)] private static extern SafeFileHandle CreateJobObject(IntPtr attributes, string? name);
    [DllImport("kernel32.dll", SetLastError = true)] private static extern bool SetInformationJobObject(SafeFileHandle job, int type, ref Limits limits, uint size);
    [DllImport("kernel32.dll", SetLastError = true)] private static extern bool AssignProcessToJobObject(SafeFileHandle job, IntPtr process);
    [DllImport("kernel32.dll", SetLastError = true)] private static extern bool IsProcessInJob(IntPtr process, SafeFileHandle job, out bool result);
    public ProcessJob()
    {
        handle = CreateJobObject(IntPtr.Zero, null);
        var limits = new Limits { Basic = new Basic { Flags = 0x2000 } };
        if (handle.IsInvalid || !SetInformationJobObject(handle, 9, ref limits, (uint)Marshal.SizeOf<Limits>())) { handle.Dispose(); throw new Win32Exception(); }
    }
    public void Attach(Process process)
    {
        if (!AssignProcessToJobObject(handle, process.Handle)) { try { process.Kill(true); } catch { } throw new Win32Exception(); }
    }
    public void Dispose() => handle.Dispose();
    public bool Owns(int pid)
    {
        try { using var process = Process.GetProcessById(pid); return IsProcessInJob(process.Handle, handle, out var member) && member; }
        catch (ArgumentException) { return false; } catch (Win32Exception) { return false; }
    }
}

public static class TcpOwner
{
    [DllImport("iphlpapi.dll")] private static extern uint GetExtendedTcpTable(IntPtr table, ref int size, bool sort, int family, int tableClass, uint reserved);
    public static int? Listener(int port, bool loopbackOnly = false)
    {
        int size = 0; GetExtendedTcpTable(IntPtr.Zero, ref size, false, 2, 5, 0);
        var buffer = Marshal.AllocHGlobal(size);
        try
        {
            if (GetExtendedTcpTable(buffer, ref size, false, 2, 5, 0) != 0) throw new IOException("Não foi possível verificar o dono da conexão local.");
            var count = Marshal.ReadInt32(buffer);
            for (var i = 0; i < count; i++)
            {
                var row = IntPtr.Add(buffer, 4 + i * 24);
                var state = Marshal.ReadInt32(row); var encoded = Marshal.ReadInt32(row, 8);
                var actual = (ushort)System.Net.IPAddress.NetworkToHostOrder(unchecked((short)encoded));
                if (state == 2 && actual == port)
                {
                    var address = unchecked((uint)Marshal.ReadInt32(row, 4));
                    if (!loopbackOnly || address == 0 || address == BitConverter.ToUInt32(System.Net.IPAddress.Loopback.GetAddressBytes()))
                        return Marshal.ReadInt32(row, 20);
                }
            }
            return null;
        }
        finally { Marshal.FreeHGlobal(buffer); }
    }
}

public static class Processes
{
    public static ProcessStartInfo Info(string executable, IEnumerable<string> args, string directory, Dictionary<string, string>? env)
    {
        var info = new ProcessStartInfo(executable) { UseShellExecute = false, CreateNoWindow = true, WorkingDirectory = directory, RedirectStandardOutput = true, RedirectStandardError = true };
        foreach (var arg in args) info.ArgumentList.Add(arg);
        // Do not inherit interpreter/package-manager injection settings from the shell.
        foreach (var key in info.Environment.Keys.Where(k => k.StartsWith("PYTHON", StringComparison.OrdinalIgnoreCase) || k.StartsWith("PIP_", StringComparison.OrdinalIgnoreCase) || k.StartsWith("UV_", StringComparison.OrdinalIgnoreCase)).ToList()) info.Environment.Remove(key);
        if (env != null) foreach (var (key, value) in env) info.Environment[key] = value;
        info.Environment["PYTHONUTF8"] = "1";
        info.Environment["PYTHONIOENCODING"] = "utf-8";
        return info;
    }
    public static async Task<string> RunAsync(string executable, IEnumerable<string> args, string directory, Dictionary<string, string>? env, CancellationToken token, TimeSpan? timeout = null)
    {
        using var time = CancellationTokenSource.CreateLinkedTokenSource(token); time.CancelAfter(timeout ?? TimeSpan.FromMinutes(45));
        using var job = new ProcessJob(); using var process = new Process { StartInfo = Info(executable, args, directory, env) };
        process.Start(); job.Attach(process);
        var output = DrainAsync(process.StandardOutput); var errors = DrainAsync(process.StandardError);
        try { await process.WaitForExitAsync(time.Token); }
        catch { try { process.Kill(true); } catch { } await process.WaitForExitAsync(); throw; }
        var text = await output; var error = await errors;
        if (process.ExitCode != 0) throw new IOException($"{Path.GetFileName(executable)} terminou com código {process.ExitCode}.\n{error[^Math.Min(2000, error.Length)..]}");
        return text;
    }
    public static async Task<string> DrainAsync(StreamReader reader)
    {
        var buffer = new char[4096]; var tail = ""; int count;
        while ((count = await reader.ReadAsync(buffer)) > 0) { tail += new string(buffer, 0, count); if (tail.Length > 12000) tail = tail[^12000..]; }
        return tail;
    }
    public static async Task DiscardAsync(StreamReader reader) { var buffer = new char[4096]; while (await reader.ReadAsync(buffer) > 0) { } }
}
