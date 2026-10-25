package com.wildfire.client.music;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/** Returns only browser PIDs using our exact private profile. Never returns command lines or cookies. */
final class MusicProfileProcesses {
    private MusicProfileProcesses() {}
    static List<ProcessHandle> find(Path profile) throws IOException {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return List.of();
        String script = """
            $ErrorActionPreference='Stop'
            $wanted=[IO.Path]::GetFullPath($env:SP_MUSIC_PROFILE)
            Get-CimInstance Win32_Process -Filter "name = 'msedge.exe' OR name = 'chrome.exe'" |
            ForEach-Object {
                $cmd=$_.CommandLine
                if($cmd -and $cmd -notmatch '(?:^|\\s)--type=') {
                    $m=[regex]::Match($cmd,'(?:^|\\s)--user-data-dir=(?:"([^"]+)"|([^\\s]+))')
                    if($m.Success) {
                        $value=if($m.Groups[1].Success){$m.Groups[1].Value}else{$m.Groups[2].Value}
                        if([string]::Equals([IO.Path]::GetFullPath($value),$wanted,[StringComparison]::OrdinalIgnoreCase)) {
                            [string]$_.ProcessId
                        }
                    }
                }
            }
            """;
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        Path powershell = Path.of(System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        ProcessBuilder builder = new ProcessBuilder(powershell.toString(), "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
        builder.environment().put("SP_MUSIC_PROFILE", profile.toAbsolutePath().normalize().toString());
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process query = builder.start();
        try {
            if(!query.waitFor(5,TimeUnit.SECONDS)) throw new IOException("Music profile process lookup timed out");
            if(query.exitValue()!=0)throw new IOException("Music profile process lookup failed");
            String output=new String(query.getInputStream().readNBytes(4096),StandardCharsets.UTF_8);
            List<ProcessHandle> result=new ArrayList<>();
            for(String line:output.split("\\R")) {
                String value=line.strip();
                if(value.matches("[0-9]{1,12}"))ProcessHandle.of(Long.parseLong(value)).ifPresent(result::add);
            }
            return List.copyOf(result);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();throw new IOException(e);
        } finally { if(query.isAlive())query.destroy(); }
    }
}
