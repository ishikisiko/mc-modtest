// DevHost: the launcher-side half of DevBridge.
//
// DevBridge lives inside Minecraft, so it cannot replace its own jars, start the game or
// survive a crash. DevHost runs next to the launcher in the user's desktop session and offers
// a fixed set of verbs over a token-protected HTTP API: status, stop, install/remove a mod
// jar (uploaded, or downloaded here from a URL and checked against a sha256), launch (and open a
// world), and read logs and crash reports. No arbitrary commands.
//
// Run with a JDK (source-file mode, no build step):
//     java DevHost.java devhost.properties
// See devhost.properties.example for the settings.

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DevHost {
    static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().startsWith("windows");
    static final Pattern JAR_NAME = Pattern.compile("[A-Za-z0-9._+\\-]+\\.jar");
    static final Pattern LOG_NAME = Pattern.compile("[A-Za-z0-9._\\-]+");
    static final String OPEN_WORLD_FILE = "devbridge-open-world.txt";
    static final String VERSION = "0.2.1";
    static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    static final Pattern PROXY = Pattern.compile("([A-Za-z0-9.\\-]+):(\\d{1,5})");

    static Path configFile, home, gameDir, modsDir, launchScript, actionLog, stateFile;
    static String token;
    static int stopTimeoutSec;
    static int maxPullBytes;
    static boolean noPauseOnLostFocus;
    static List<String> downloadProxies = List.of();
    /** stop / install / remove / launch run one at a time. */
    static final ReentrantLock OPS = new ReentrantLock();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public static void main(String[] args) throws Exception {
        configFile = Path.of(args.length > 0 ? args[0] : "devhost.properties").toAbsolutePath();
        home = configFile.getParent();
        actionLog = home.resolve("devhost.log");
        try {
            start();
        } catch (Exception e) {
            log("startup failed: " + trace(e));
            throw e;
        }
    }

    static void start() throws Exception {
        Properties cfg = new Properties();
        try (var r = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            cfg.load(r);
        }
        maxPullBytes = readMaxPullBytes(cfg);
        token = cfg.getProperty("token", "").strip();
        if (token.isEmpty()) {
            byte[] b = new byte[24];
            new SecureRandom().nextBytes(b);
            token = HexFormat.of().formatHex(b);
            Files.writeString(configFile, "\ntoken=" + token + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            log("generated a token in " + configFile);
        }
        gameDir = Path.of(require(cfg, "gameDir")).toAbsolutePath().normalize();
        modsDir = gameDir.resolve("mods");
        launchScript = Path.of(require(cfg, "launch")).toAbsolutePath().normalize();
        stopTimeoutSec = Integer.parseInt(cfg.getProperty("stopTimeoutSec", "60").strip());
        noPauseOnLostFocus = Boolean.parseBoolean(cfg.getProperty("noPauseOnLostFocus", "true").strip());
        downloadProxies = parseProxies(System.getProperty("devhost.downloadProxies", ""));
        stateFile = home.resolve("devhost-state.properties");
        String bind = cfg.getProperty("bind", "auto").strip();
        InetAddress addr = bind.equals("auto") ? tailscaleAddress() : InetAddress.getByName(bind);
        int port = Integer.parseInt(cfg.getProperty("port", "8790").strip());

        HttpServer server = HttpServer.create(new InetSocketAddress(addr, port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        route(server, "/ping", false, q -> Map.of("devhost", true, "os", System.getProperty("os.name"),
                "version", VERSION, "features", List.of("install-url")));
        route(server, "/status", true, q -> status());
        route(server, "/stop", true, q -> exclusive(() -> stop(intParam(q, "timeoutSec", stopTimeoutSec))));
        route(server, "/mods/remove", true, q -> exclusive(() -> remove(param(q, "name", null))));
        route(server, "/launch", true, q -> exclusive(() -> launch(q.containsKey("world") ? q.get("world") : null)));
        route(server, "/log", true, q -> readLog(param(q, "name", "latest.log"), intParam(q, "lines", 200)));
        route(server, "/crash", true, q -> readCrash(param(q, "name", null), intParam(q, "maxChars", 20000)));
        route(server, "/mods/install-url", true, q -> exclusive(() -> installFromUrl(q)));
        server.createContext("/mods/install", ex -> handle(ex, true, q -> exclusive(() -> install(q, ex.getRequestBody().readAllBytes()))));
        server.start();
        log("listening on http://" + addr.getHostAddress() + ":" + port + "  gameDir=" + gameDir + "  launch=" + launchScript);
    }

    // ---------------------------------------------------------------- verbs

    static Object status() throws Exception {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("devhostVersion", VERSION);
        o.put("maxPullMiB", maxPullBytes / (1024 * 1024));
        List<Long> pids = gamePids();
        boolean bridge = bridgeUp();
        o.put("running", !pids.isEmpty() || bridge);
        o.put("pids", pids);
        o.put("bridge", bridge);
        o.put("lastWorld", state("lastWorld"));
        o.put("gameDir", gameDir.toString());
        o.put("launch", launchScript.toString());
        o.put("launchExists", Files.isRegularFile(launchScript));
        o.put("mods", listJars());
        return o;
    }

    static Object stop(int timeoutSec) throws Exception {
        Map<String, Object> o = new LinkedHashMap<>();
        boolean running = gameRunning();
        o.put("wasRunning", running);
        if (!running) return o;
        String world = currentWorld();
        if (world != null) setState("lastWorld", world);
        o.put("world", world);
        boolean asked = bridgeCall("POST", "/client/quit") != null;
        o.put("askedToQuit", asked);
        long start = System.nanoTime();
        while (gameRunning() && System.nanoTime() - start < timeoutSec * 1_000_000_000L) Thread.sleep(1000);
        boolean forced = false;
        List<Long> left = gamePids();
        if (!left.isEmpty()) {
            for (long pid : left) kill(pid);
            forced = true;
            Thread.sleep(2000);
        } else if (bridgeUp()) {
            log("stop: DevBridge still answers but no game process was found to kill");
        }
        o.put("forced", forced);
        o.put("seconds", (System.nanoTime() - start) / 1_000_000_000L);
        o.put("stillRunning", gameRunning());
        log("stop: world=" + world + " forced=" + forced);
        return o;
    }

    static Object install(Map<String, String> q, byte[] jar) throws Exception {
        String name = param(q, "name", null);
        String sha = param(q, "sha256", null);
        if (name == null || !JAR_NAME.matcher(name).matches()) throw new HttpError(400, "name must be a plain .jar file name");
        if (sha == null) throw new HttpError(400, "sha256 is required");
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar));
        if (!actual.equalsIgnoreCase(sha)) throw new HttpError(400, "sha256 mismatch: got " + actual);
        if (gameRunning()) throw new HttpError(409, "the game is running (its jars are locked); stop it first");
        List<String> ids = modIds(new ByteArrayInputStream(jar));
        if (ids.isEmpty()) throw new HttpError(400, "no [[mods]] modId in META-INF/neoforge.mods.toml or mods.toml");

        Path backup = null;
        List<String> replaced = new ArrayList<>();
        for (Path existing : jarPaths()) {
            boolean sameFile = existing.getFileName().toString().equals(name);
            boolean sameMod = false;
            try (InputStream in = Files.newInputStream(existing)) {
                sameMod = !Collections.disjoint(ids, modIds(in));
            } catch (IOException e) {
                // unreadable jar: leave it alone
            }
            if (sameFile || sameMod) {
                if (backup == null) backup = newBackupDir();
                Files.move(existing, backup.resolve(existing.getFileName()));
                replaced.add(existing.getFileName().toString());
            }
        }
        Path tmp = modsDir.resolve(name + ".devhost-tmp");
        Files.write(tmp, jar);
        Files.move(tmp, modsDir.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        log("install: " + name + " modIds=" + ids + " replaced=" + replaced);
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("installed", name);
        o.put("modIds", ids);
        o.put("replaced", replaced);
        o.put("backup", backup == null ? null : backup.toString());
        return o;
    }

    /**
     * install(), with the jar downloaded by this machine instead of uploaded to it: the caller's link to
     * here can be far slower than this machine's own downloads. The sha256 comes over this authenticated
     * API, so the url may be plain http. 省略 proxy 使用启动时的代理顺序；空值明确直连。
     */
    static Object installFromUrl(Map<String, String> q) throws Exception {
        String name = param(q, "name", null);
        String sha = param(q, "sha256", null);
        List<String> proxies = proxiesFor(q);
        int timeoutSec = intParam(q, "timeoutSec", 300);
        if (timeoutSec < 1) throw new HttpError(400, "timeoutSec must be positive");
        if (name == null || !JAR_NAME.matcher(name).matches()) throw new HttpError(400, "name must be a plain .jar file name");
        if (sha == null || !SHA256.matcher(sha).matches()) throw new HttpError(400, "sha256 (64 hex digits) is required");
        URI uri;
        try {
            uri = URI.create(param(q, "url", ""));
        } catch (IllegalArgumentException e) {
            throw new HttpError(400, "url is not a valid URL");
        }
        if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
            throw new HttpError(400, "url must be http(s)://host/...");
        }
        if (gameRunning()) throw new HttpError(409, "the game is running (its jars are locked); stop it first");

        long start = System.nanoTime();
        Download downloaded = downloadFromUrl(uri, proxies, timeoutSec);
        byte[] jar = downloaded.bytes();
        String via = downloaded.via();
        long millis = (System.nanoTime() - start) / 1_000_000L;

        @SuppressWarnings("unchecked")
        Map<String, Object> o = (Map<String, Object>) install(q, jar);
        o.put("bytes", jar.length);
        o.put("downloadMillis", millis);
        o.put("via", via);
        log("install-url: " + name + " " + jar.length + " bytes in " + millis + " ms via " + via + " from " + uri);
        return o;
    }

    static List<String> parseProxies(String value) throws HttpError {
        if (value.isBlank()) return List.of();
        List<String> proxies = new ArrayList<>();
        for (String part : value.split(",", -1)) {
            String proxy = part.strip();
            proxyAddress(proxy);
            if (!proxies.contains(proxy)) proxies.add(proxy);
        }
        return List.copyOf(proxies);
    }

    static InetSocketAddress proxyAddress(String proxy) throws HttpError {
        Matcher m = PROXY.matcher(proxy);
        if (!m.matches()) throw new HttpError(400, "proxy must be host:port");
        int port = Integer.parseInt(m.group(2));
        if (port < 1 || port > 65535) throw new HttpError(400, "proxy port must be 1..65535");
        return new InetSocketAddress(m.group(1), port);
    }

    static List<String> proxiesFor(Map<String, String> q) throws HttpError {
        if (!q.containsKey("proxy")) return downloadProxies.isEmpty() ? List.of("") : downloadProxies;
        String proxy = q.get("proxy").strip();
        if (proxy.isEmpty()) return List.of("");
        proxyAddress(proxy);
        int index = downloadProxies.indexOf(proxy);
        // 指定默认链中的代理时，仍允许回退；其他地址只使用调用方指定的代理。
        return index < 0 ? List.of(proxy) : downloadProxies.subList(index, downloadProxies.size());
    }

    record Download(byte[] bytes, String via) {}

    static class DownloadFailure extends HttpError {
        DownloadFailure(int status, String message) { super(status, message); }
    }

    static class DownloadTooLarge extends RuntimeException {}

    static int readMaxPullBytes(Properties cfg) {
        try {
            int mib = Integer.parseInt(cfg.getProperty("maxPullMiB", "256").strip());
            if (mib >= 1 && mib <= 1024) return mib * 1024 * 1024;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("maxPullMiB 必须为 1–1024 的整数");
    }

    /** Enforce the same limit when a server omits Content-Length or streams chunks. */
    static class LimitedDownload implements HttpResponse.BodySubscriber<byte[]> {
        final HttpResponse.BodySubscriber<byte[]> body = HttpResponse.BodySubscribers.ofByteArray();
        final int maximum;
        Flow.Subscription subscription;
        long received;
        boolean finished;

        LimitedDownload(int maximum) { this.maximum = maximum; }
        public CompletionStage<byte[]> getBody() { return body.getBody(); }
        public void onSubscribe(Flow.Subscription next) { subscription = next; body.onSubscribe(next); }
        public void onNext(List<ByteBuffer> buffers) {
            if (finished) return;
            for (ByteBuffer buffer : buffers) {
                received += buffer.remaining();
                if (received > maximum) {
                    finished = true;
                    body.onError(new DownloadTooLarge());
                    subscription.cancel();
                    return;
                }
            }
            body.onNext(buffers);
        }
        public void onError(Throwable error) { if (!finished) { finished = true; body.onError(error); } }
        public void onComplete() { if (!finished) { finished = true; body.onComplete(); } }
    }

    static Download downloadFromUrl(URI uri, List<String> proxies, int timeoutSec) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec);
        List<String> failures = new ArrayList<>();
        int status = 502;
        for (int i = 0; i < proxies.size(); i++) {
            String proxy = proxies.get(i);
            String via = proxy.isEmpty() ? "direct" : "proxy " + proxy;
            // 所有尝试共享总超时，并给后续代理保留时间，避免首选卡住后无法回退。
            long budget = (deadline - System.nanoTime()) / (proxies.size() - i);
            if (budget <= 0) { status = 504; break; }
            try {
                return new Download(downloadAttempt(uri, proxy, budget), via);
            } catch (DownloadFailure e) {
                status = e.status;
                failures.add(via + ": " + e.getMessage());
                log("install-url 下载失败 " + via + ": " + e.getMessage()
                        + (i + 1 < proxies.size() ? "；尝试备用代理" : ""));
            }
        }
        throw new HttpError(status, "download failed: " + String.join("; ", failures));
    }

    static byte[] downloadAttempt(URI uri, String proxy, long budgetNanos) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofNanos(Math.min(TimeUnit.SECONDS.toNanos(10), budgetNanos)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(ProxySelector.of(proxy.isEmpty() ? null : proxyAddress(proxy))).build();
        CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(HttpRequest.newBuilder(uri).GET().build(), info -> {
            // replacing(null) 仍会消费整个响应；传输中断会掩盖超限原因，错误触发回退。
            if (info.headers().firstValueAsLong("Content-Length").orElse(0) > maxPullBytes) throw new DownloadTooLarge();
            return new LimitedDownload(maxPullBytes);
        });
        try {
            HttpResponse<byte[]> r = pending.get(budgetNanos, TimeUnit.NANOSECONDS);
            int code = r.statusCode();
            if (code == 502 || code == 503 || code == 504) throw new DownloadFailure(502, "HTTP " + code);
            if (code != 200) throw new HttpError(502, "download failed: HTTP " + code + " from " + uri);
            byte[] jar = r.body();
            if (jar == null || jar.length > maxPullBytes) throw new HttpError(413, "the download is larger than " + maxPullBytes + " bytes");
            return jar;
        } catch (TimeoutException e) {
            throw new DownloadFailure(504, "download timed out");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof DownloadTooLarge) throw new HttpError(413, "the download is larger than " + maxPullBytes + " bytes");
            if (e.getCause() instanceof IOException) throw new DownloadFailure(502, e.getCause().toString());
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            pending.cancel(true);
            client.shutdownNow();
        }
    }

    static Object remove(String name) throws Exception {
        if (name == null || !JAR_NAME.matcher(name).matches()) throw new HttpError(400, "name must be a plain .jar file name");
        if (gameRunning()) throw new HttpError(409, "the game is running (its jars are locked); stop it first");
        Path jar = modsDir.resolve(name);
        if (!Files.isRegularFile(jar)) throw new HttpError(404, "no mods/" + name);
        Path backup = newBackupDir();
        Files.move(jar, backup.resolve(name));
        log("remove: " + name);
        return Map.of("removed", name, "backup", backup.toString());
    }

    /** world: null = the last world stop() saw, "" = stay at the title screen, else a saves/ folder name. */
    static Object launch(String world) throws Exception {
        if (gameRunning()) throw new HttpError(409, "the game is already running");
        if (!Files.isRegularFile(launchScript)) throw new HttpError(404, "launch script not found: " + launchScript);
        if (world == null) world = state("lastWorld");
        Path request = gameDir.resolve("config").resolve(OPEN_WORLD_FILE);
        if (world != null && !world.isEmpty()) {
            if (!Files.isRegularFile(gameDir.resolve("saves").resolve(world).resolve("level.dat"))) {
                throw new HttpError(404, "no world folder saves/" + world);
            }
            Files.createDirectories(request.getParent());
            Files.writeString(request, world, StandardCharsets.UTF_8);
        } else {
            Files.deleteIfExists(request);
        }
        if (noPauseOnLostFocus) setOption("pauseOnLostFocus", "false");

        Path out = gameDir.resolve("logs").resolve("devhost-launch.log");
        Files.createDirectories(out.getParent());
        ProcessBuilder pb = WINDOWS
                ? new ProcessBuilder("cmd.exe", "/c", launchScript.toString())
                : new ProcessBuilder("sh", launchScript.toString());
        pb.directory(launchScript.getParent().toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.to(out.toFile()));
        Process p = pb.start();
        p.getOutputStream().close(); // a trailing "pause" in the script must not wait forever
        launched = p;
        log("launch: world=" + world + " pid=" + p.pid());
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("launcherPid", p.pid());
        o.put("world", world);
        o.put("output", out.toString());
        return o;
    }

    static Object readLog(String name, int lines) throws Exception {
        if (!LOG_NAME.matcher(name).matches()) throw new HttpError(400, "bad log name");
        Path file = gameDir.resolve("logs").resolve(name);
        if (!Files.isRegularFile(file)) throw new HttpError(404, "no logs/" + name);
        List<String> all = readLines(file);
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("name", name);
        o.put("totalLines", all.size());
        o.put("lines", all.subList(Math.max(0, all.size() - lines), all.size()));
        return o;
    }

    static Object readCrash(String name, int maxChars) throws Exception {
        Path dir = gameDir.resolve("crash-reports");
        List<Path> reports = new ArrayList<>();
        if (Files.isDirectory(dir)) try (Stream<Path> s = Files.list(dir)) {
            s.filter(f -> f.getFileName().toString().endsWith(".txt")).forEach(reports::add);
        }
        reports.sort(Comparator.comparingLong(DevHost::mtime).reversed());
        Map<String, Object> o = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();
        for (Path r : reports) names.add(r.getFileName().toString());
        o.put("reports", names.subList(0, Math.min(10, names.size())));
        Path pick = null;
        if (name != null) {
            if (!LOG_NAME.matcher(name).matches()) throw new HttpError(400, "bad report name");
            pick = dir.resolve(name);
            if (!Files.isRegularFile(pick)) throw new HttpError(404, "no crash-reports/" + name);
        } else if (!reports.isEmpty()) {
            pick = reports.get(0);
        }
        if (pick != null) {
            String text = String.join("\n", readLines(pick));
            o.put("name", pick.getFileName().toString());
            o.put("modified", mtime(pick));
            o.put("text", text.length() > maxChars ? text.substring(0, maxChars) : text);
        }
        return o;
    }

    // ---------------------------------------------------------------- game process

    /** A game process was found, or DevBridge answers (which means a game is up even if the process match failed). */
    static boolean gameRunning() throws Exception {
        return !gamePids().isEmpty() || bridgeUp();
    }

    /** The launch script process from the last launch(); its java children are the game. */
    static Process launched;

    /**
     * The game's JVMs, by any of: the command line mentions the game directory (launchers pass
     * --gameDir; on Linux also a working directory equal to it), the pid DevBridge reports, or a
     * java process under the last launch script.
     */
    static List<Long> gamePids() throws Exception {
        long self = ProcessHandle.current().pid();
        List<Long> out = new ArrayList<>();
        String dir = gameDir.toString();
        if (WINDOWS) {
            // Java cannot read other processes' arguments on Windows; CIM can. The script goes in
            // as -EncodedCommand: Java does not escape embedded double quotes in Windows arguments.
            String ps = "Get-CimInstance Win32_Process | "
                    + "Where-Object { ($_.Name -eq 'javaw.exe' -or $_.Name -eq 'java.exe') -and $_.CommandLine -and "
                    + "$_.CommandLine.ToLower().Contains('" + dir.toLowerCase().replace("'", "''") + "') } | "
                    + "ForEach-Object { $_.ProcessId }";
            String encoded = java.util.Base64.getEncoder().encodeToString(ps.getBytes(StandardCharsets.UTF_16LE));
            for (String line : run("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)) {
                line = line.strip();
                if (line.matches("\\d+")) addPid(out, Long.parseLong(line), self);
            }
        } else {
            ProcessHandle.allProcesses()
                    .filter(DevHost::isJava)
                    .filter(h -> h.info().commandLine().map(c -> c.contains(dir)).orElse(false) || cwdIsGameDir(h.pid()))
                    .forEach(h -> addPid(out, h.pid(), self));
        }
        Long bridgePid = bridgePid();
        if (bridgePid != null && ProcessHandle.of(bridgePid).map(ProcessHandle::isAlive).orElse(false)) addPid(out, bridgePid, self);
        if (launched != null) launched.descendants().filter(DevHost::isJava).forEach(h -> addPid(out, h.pid(), self));
        return out;
    }

    static void addPid(List<Long> out, long pid, long self) {
        if (pid != self && !out.contains(pid)) out.add(pid);
    }

    static boolean isJava(ProcessHandle h) {
        return h.info().command().map(c -> c.toLowerCase().matches(".*[\\\\/]javaw?(\\.exe)?$")).orElse(false);
    }

    static boolean cwdIsGameDir(long pid) {
        try {
            return Files.isSameFile(Path.of("/proc", Long.toString(pid), "cwd").toRealPath(), gameDir);
        } catch (Exception e) {
            return false;
        }
    }

    static void kill(long pid) throws Exception {
        if (WINDOWS) {
            run("taskkill.exe", "/PID", Long.toString(pid), "/T", "/F");
        } else {
            ProcessHandle.of(pid).ifPresent(h -> {
                h.descendants().forEach(ProcessHandle::destroyForcibly);
                h.destroyForcibly();
            });
        }
    }

    static List<String> run(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.getOutputStream().close();
        String text = new String(p.getInputStream().readAllBytes(), nativeCharset());
        p.waitFor();
        return text.lines().toList();
    }

    // ---------------------------------------------------------------- DevBridge (inside the game)

    static String bridgeUrl, bridgeToken;

    /** Reads config/devbridge.json each time: the game writes it on first start. */
    static boolean loadBridgeConfig() {
        Path f = gameDir.resolve("config").resolve("devbridge.json");
        if (!Files.isRegularFile(f)) return false;
        try {
            String json = Files.readString(f, StandardCharsets.UTF_8);
            String bind = jsonValue(json, "bind");
            String port = jsonValue(json, "port");
            bridgeToken = jsonValue(json, "token");
            if (bind == null || bind.equals("0.0.0.0")) bind = "127.0.0.1";
            bridgeUrl = "http://" + bind + ":" + (port == null ? "8787" : port);
            return bridgeToken != null;
        } catch (IOException e) {
            return false;
        }
    }

    static String jsonValue(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^\",}\\s]+)\"?").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static boolean bridgeUp() {
        return bridgePing() != null;
    }

    /** DevBridge's /ping body, or null when it does not answer. */
    static String bridgePing() {
        if (!loadBridgeConfig()) return null;
        try {
            var r = HTTP.send(HttpRequest.newBuilder(URI.create(bridgeUrl + "/ping")).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200 ? r.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** The game's pid as DevBridge reports it (0.4.0+), or null. */
    static Long bridgePid() {
        String body = bridgePing();
        if (body == null) return null;
        Matcher m = Pattern.compile("\"pid\":(\\d+)").matcher(body);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    /** Calls DevBridge; null when it is not reachable or answers with an error. */
    static String bridgeCall(String method, String path) {
        if (!loadBridgeConfig()) return null;
        try {
            var b = HttpRequest.newBuilder(URI.create(bridgeUrl + path)).timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + bridgeToken);
            b = method.equals("POST") ? b.POST(HttpRequest.BodyPublishers.ofString("{}")) : b.GET();
            var r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return r.statusCode() == 200 && r.body().contains("\"ok\":true") ? r.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    static String currentWorld() {
        String body = bridgeCall("GET", "/client/world");
        if (body == null || !body.contains("\"singleplayer\":true")) return null;
        Matcher m = Pattern.compile("\"folder\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(body);
        return m.find() ? unescapeJson(m.group(1)) : null;
    }

    // ---------------------------------------------------------------- files

    static List<Path> jarPaths() throws IOException {
        List<Path> out = new ArrayList<>();
        if (Files.isDirectory(modsDir)) try (Stream<Path> s = Files.list(modsDir)) {
            s.filter(f -> f.getFileName().toString().endsWith(".jar") && Files.isRegularFile(f)).sorted().forEach(out::add);
        }
        return out;
    }

    static List<String> listJars() throws IOException {
        List<String> out = new ArrayList<>();
        for (Path p : jarPaths()) out.add(p.getFileName().toString());
        return out;
    }

    /** modIds declared under [[mods]] (not under [[dependencies.*]]). */
    static List<String> modIds(InputStream jar) throws IOException {
        List<String> ids = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(jar)) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                String n = e.getName();
                if (!n.equals("META-INF/neoforge.mods.toml") && !n.equals("META-INF/mods.toml")) continue;
                boolean inMods = false;
                for (String line : new String(zip.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                    String t = line.strip();
                    if (t.startsWith("[")) inMods = t.equals("[[mods]]");
                    Matcher m = Pattern.compile("^modId\\s*=\\s*\"([^\"]+)\"").matcher(t);
                    if (inMods && m.find() && !ids.contains(m.group(1))) ids.add(m.group(1));
                }
            }
        }
        return ids;
    }

    static Path newBackupDir() throws IOException {
        Path dir = gameDir.resolve("devhost-backup").resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        Files.createDirectories(dir);
        return dir;
    }

    /** Sets key:value in the game's options.txt if the file exists (the game creates it). */
    static void setOption(String key, String value) throws IOException {
        Path f = gameDir.resolve("options.txt");
        if (!Files.isRegularFile(f)) return;
        List<String> lines = new ArrayList<>(Files.readAllLines(f, StandardCharsets.UTF_8));
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(key + ":")) {
                lines.set(i, key + ":" + value);
                found = true;
            }
        }
        if (!found) lines.add(key + ":" + value);
        Files.write(f, lines, StandardCharsets.UTF_8);
    }

    static List<String> readLines(Path f) throws IOException {
        byte[] b = Files.readAllBytes(f);
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b)).toString().lines().toList();
        } catch (java.nio.charset.CharacterCodingException e) {
            return new String(b, nativeCharset()).lines().toList(); // e.g. GBK output on Chinese Windows
        }
    }

    static long mtime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    static String state(String key) {
        Properties p = new Properties();
        if (Files.isRegularFile(stateFile)) try (var r = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException ignored) {
        }
        return p.getProperty(key);
    }

    static void setState(String key, String value) throws IOException {
        Properties p = new Properties();
        if (Files.isRegularFile(stateFile)) try (var r = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        p.setProperty(key, value);
        try (var w = Files.newBufferedWriter(stateFile, StandardCharsets.UTF_8)) {
            p.store(w, "DevHost state");
        }
    }

    // ---------------------------------------------------------------- HTTP plumbing

    interface Handler { Object handle(Map<String, String> query) throws Exception; }
    interface Op { Object run() throws Exception; }

    static class HttpError extends Exception {
        final int status;
        HttpError(int status, String msg) { super(msg); this.status = status; }
    }

    static Object exclusive(Op op) throws Exception {
        if (!OPS.tryLock()) throw new HttpError(409, "another stop/install/remove/launch is in progress");
        try {
            return op.run();
        } finally {
            OPS.unlock();
        }
    }

    static void route(HttpServer server, String path, boolean auth, Handler h) {
        server.createContext(path, ex -> handle(ex, auth, h));
    }

    static void handle(HttpExchange ex, boolean auth, Handler h) throws IOException {
        int status = 200;
        String body;
        try {
            if (!ex.getRequestURI().getPath().equals(ex.getHttpContext().getPath())) throw new HttpError(404, "no route");
            if (auth && !authorized(ex)) throw new HttpError(401, "missing or invalid bearer token");
            body = "{\"ok\":true,\"result\":" + json(h.handle(query(ex))) + "}";
        } catch (HttpError e) {
            status = e.status;
            body = "{\"ok\":false,\"error\":" + json(e.getMessage()) + "}";
        } catch (Exception e) {
            status = 500;
            log("error on " + ex.getRequestURI().getPath() + ": " + trace(e));
            body = "{\"ok\":false,\"error\":" + json(e.toString()) + "}";
        }
        byte[] out = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, out.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(out);
        }
    }

    static boolean authorized(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        String given = h != null && h.startsWith("Bearer ") ? h.substring(7).strip() : "";
        return MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    static Map<String, String> query(HttpExchange ex) {
        Map<String, String> q = new LinkedHashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return q;
        for (String part : raw.split("&")) {
            if (part.isEmpty()) continue;
            int i = part.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? part : part.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8);
            q.put(k, v);
        }
        return q;
    }

    static String param(Map<String, String> q, String key, String def) {
        String v = q.get(key);
        return v == null || v.isEmpty() ? def : v;
    }

    static int intParam(Map<String, String> q, String key, int def) throws HttpError {
        String v = param(q, key, null);
        if (v == null) return def;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new HttpError(400, key + " must be an integer");
        }
    }

    static String json(Object v) {
        if (v == null) return "null";
        if (v instanceof Boolean || v instanceof Number) return v.toString();
        if (v instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{");
            for (var e : m.entrySet()) {
                if (sb.length() > 1) sb.append(',');
                sb.append(json(String.valueOf(e.getKey()))).append(':').append(json(e.getValue()));
            }
            return sb.append('}').toString();
        }
        if (v instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder("[");
            for (Object o : it) {
                if (sb.length() > 1) sb.append(',');
                sb.append(json(o));
            }
            return sb.append(']').toString();
        }
        String s = v.toString();
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    static String unescapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'u' -> {
                    sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> sb.append(n);
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- misc

    /** The first address in Tailscale's 100.64.0.0/10 range; 127.0.0.1 if there is none. */
    static InetAddress tailscaleAddress() throws IOException {
        for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            for (InetAddress a : Collections.list(nif.getInetAddresses())) {
                byte[] b = a.getAddress();
                if (a instanceof Inet4Address && (b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64) return a;
            }
        }
        log("no Tailscale address found; binding to 127.0.0.1 only");
        return InetAddress.getLoopbackAddress();
    }

    static Charset nativeCharset() {
        String n = System.getProperty("native.encoding");
        try {
            return n == null ? Charset.defaultCharset() : Charset.forName(n);
        } catch (Exception e) {
            return Charset.defaultCharset();
        }
    }

    static String require(Properties cfg, String key) {
        String v = cfg.getProperty(key, "").strip();
        if (v.isEmpty()) throw new IllegalStateException(key + " is not set in " + configFile);
        return v;
    }

    static synchronized void log(String msg) {
        String line = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "  " + msg + System.lineSeparator();
        System.out.print(line);
        try {
            Files.writeString(actionLog, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    static String trace(Throwable t) {
        StringWriter w = new StringWriter();
        t.printStackTrace(new PrintWriter(w));
        return w.toString();
    }
}
