package com.ultikits.ultitools.utils;

import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSONObject;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.entities.TokenEntity;
import com.ultikits.ultitools.websocket.WebsocketClient;
import io.socket.client.Ack;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.ultikits.ultitools.utils.CommonUtils.getUltiToolsUUID;
import static com.ultikits.ultitools.utils.VersionUtils.getUltiToolsNewestVersion;
import static org.bukkit.Bukkit.getServer;

public class PluginInitiationUtils {
    private static WebsocketClient panelWS;
    private static TokenEntity token;

    /**
     * Login account.
     * <br>
     * 登录账户。
     *
     * @throws IOException if an I/O error occurs
     */
    public static boolean loginAccount(String username, String password) throws IOException {
        boolean ssl = UltiTools.getInstance().getConfig().getBoolean("web-editor.https.enable");
        token = HttpRequestUtils.getToken(username, password);
        String uuid = CommonUtils.getUltiToolsUUID();
        HttpResponse uuidResponse = HttpRequestUtils.getServerByUUID(uuid, token);
        int port = UltiTools.getInstance().getConfig().getInt("web-editor.port");
        String domain = UltiTools.getInstance().getConfig().getString("web-editor.https.domain");
        if (uuidResponse.getStatus() == 404) {
            try (HttpResponse registerResponse = HttpRequestUtils.registerServer(uuid, port, domain, ssl, token)) {
                if (!registerResponse.isOk()) {
                    UltiTools.getInstance().getLogger().log(Level.WARNING, registerResponse.body());
                    return false;
                }
            }
        } else {
            try (HttpResponse registerResponse = HttpRequestUtils.updateServer(uuid, port, domain, ssl, token)) {
                if (!registerResponse.isOk()) {
                    UltiTools.getInstance().getLogger().log(Level.WARNING, registerResponse.body());
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Initialize websocket.
     * <br>
     * 初始化websocket。
     *
     * @throws URISyntaxException if the URI is invalid
     */
    public static void initWebsocket() throws URISyntaxException {
        panelWS = getPanelWebsocketClient();
        UltiTools.getInstance().getLogger().log(Level.INFO, UltiTools.getInstance().i18n("Websocket已连接!"));
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("data", ConfigEditorUtils.getConfigMapString());
        jsonObject.put("comment", ConfigEditorUtils.getCommentMapString());
        jsonObject.put("serverId", panelWS.getServerId());
        UltiTools.getInstance().getLogger().log(Level.INFO, UltiTools.getInstance().i18n("正在上传本地配置..."));
        panelWS.getSocket().emit("upload_config", new JSONObject[]{jsonObject}, ack -> {
            if (ack[0].equals("ok")) {
                UltiTools.getInstance().getLogger().log(Level.INFO, UltiTools.getInstance().i18n("配置上传成功!"));
            } else {
                UltiTools.getInstance().getLogger().log(Level.WARNING, UltiTools.getInstance().i18n("配置上传失败!"));
            }
        });
    }

    public static void stopWebsocket() {
        if (panelWS == null){
            return;
        }
        panelWS.stop();
    }

    public static boolean downloadRequiredDependencies() {
        YamlConfiguration env = UltiTools.getEnv();
        List<String> missingLib = Objects.requireNonNull(getRequiredLibs())
                .stream()
                .map(lib -> new File(UltiTools.getInstance().getDataFolder() + "/lib", lib))
                .filter(file -> !file.exists()).map(File::getName)
                .collect(Collectors.toList());
        if (missingLib.isEmpty()) {
            return false;
        }
        UltiTools.getInstance().getLogger().log(Level.INFO, "Missing required libraries，trying to download...");
        UltiTools.getInstance().getLogger().log(Level.INFO, "If have problems in downloading，you can download full version.");
        for (int i = 0; i < missingLib.size(); i++) {
            String name = missingLib.get(i);
            String url = env.getString("oss-url") + env.getString("lib-path") + name;
            double i1 = (double) i / missingLib.size();
            int percentage = (int) (i1 * 100);
            printLoadingBar(percentage);
            HttpDownloadUtils.download(url, name, UltiTools.getInstance().getDataFolder() + "/lib");
        }
        printLoadingBar(100);
        UltiTools.getInstance().getLogger().log(Level.INFO, "All required libraries have been downloaded.");
        return true;
    }

    public static void printBanner() {
        InputStream in = UltiTools.getInstance().getFileResource("banner.txt");
        if (in != null) {
            new BufferedReader(new InputStreamReader(in)).lines().forEach(Bukkit.getLogger()::info);
        }
    }

    public static List<String> getRequiredLibs() {
        String PluginJarPath = UltiTools.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        try (JarFile jarFile = new JarFile(PluginJarPath)) {
            List<String> requiredLibs = new ArrayList<>();
            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                String lib = manifest.getMainAttributes().getValue("Class-Path");
                if (lib != null) {
                    String[] Libs = lib.split(" ");
                    for (String libName : Libs) {
                        requiredLibs.add(libName.replaceAll("UltiTools/lib/", ""));
                    }
                    return requiredLibs;
                }
            }
            return null;
        } catch (IOException e) {
            UltiTools.getInstance().getLogger().warning(e.getMessage());
            return null;
        }
    }

    public static URL[] getLibs() {
        File libDir = new File(UltiTools.getInstance().getDataFolder(), "lib");
        if (!libDir.exists()) {
            libDir.mkdirs();
        }
        File[] libFiles = libDir.listFiles();
        if (libFiles == null) {
            return new URL[]{getServerJar()};
        }

        List<File> files = new ArrayList<>(Arrays.asList(libFiles));
        File pluginsFolder = UltiTools.getInstance().getDataFolder().getParentFile();
        for (File file : Objects.requireNonNull(pluginsFolder.listFiles())) {
            if (file.getName().endsWith(".jar")) {
                files.add(file);
            }
        }

        File pluginDir = new File(UltiTools.getInstance().getDataFolder(), "plugins");
        if (!pluginDir.exists()) {
            pluginDir.mkdirs();
        }
        File[] pluginFiles = pluginDir.listFiles();
        if (pluginFiles != null) {
            files.addAll(Arrays.asList(pluginFiles));
        }

        URL[] urls = new URL[files.size() + 1];
        for (int i = 0; i < files.size(); i++) {
            try {
                urls[i] = files.get(i).toURI().toURL();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        urls[files.size()] = getServerJar();
        return urls;
    }

    public static void checkPluginVersion() {
        getServer().getScheduler().scheduleSyncDelayedTask(UltiTools.getInstance(), () -> {
            String ultiToolsNewestVersion = getUltiToolsNewestVersion();
            String currentVersion = UltiTools.getEnv().getString("version");
            UltiTools.getInstance().getLogger().log(Level.INFO, String.format(UltiTools.getInstance().i18n("UltiTools-API已启动，当前版本：%s"), UltiTools.getEnv().getString("version")));
            UltiTools.getInstance().getLogger().log(Level.INFO, String.format(UltiTools.getInstance().i18n("服务器UUID: %s"), getUltiToolsUUID()));
            UltiTools.getInstance().getLogger().log(Level.INFO, UltiTools.getInstance().i18n("正在检查版本更新..."));
            if (UltiTools.getInstance().getDependenceManagers().getVersionComparator().compare(currentVersion, ultiToolsNewestVersion) < 0) {
                UltiTools.getInstance().getLogger().log(Level.INFO, String.format(UltiTools.getInstance().i18n("UltiTools-API有新版本 %s 可用，请及时更新！"), ultiToolsNewestVersion));
                UltiTools.getInstance().getLogger().log(Level.INFO, String.format(UltiTools.getInstance().i18n("下载地址：%s"), "https://github.com/UltiKits/UltiTools-Reborn/releases/latest"));
                return;
            }
            UltiTools.getInstance().getLogger().log(Level.INFO, UltiTools.getInstance().i18n("UltiTools-API已是最新版本！"));
        });
    }

    public static URL getServerJar() {
        ProtectionDomain protectionDomain = Bukkit.class.getProtectionDomain();
        CodeSource codeSource = protectionDomain.getCodeSource();
        if (codeSource == null) {
            return null;
        }
        if (codeSource.getLocation().toString().startsWith("union:")) {
            String replace = codeSource.getLocation().toString().replace("union:", "file:").split("%")[0];
            try {
                return new URL(replace);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        return codeSource.getLocation();
    }

    public static void checkAccountLogin(boolean loginRequired, boolean finalLoginSuccess, String username) {
        getServer().getScheduler().scheduleSyncDelayedTask(UltiTools.getInstance(), () -> {
            if (loginRequired) {
                if (finalLoginSuccess) {
                    UltiTools.getInstance().getLogger().log(Level.INFO, String.format(UltiTools.getInstance().i18n("UltiKits账户 %s 登录成功！"), username));
                } else {
                    UltiTools.getInstance().getLogger().log(Level.WARNING, String.format(UltiTools.getInstance().i18n("UltiKits账户 %s 登录失败！云端相关功能将无法使用！"), username));
                }
            }
            if (UltiTools.getInstance().getConfig().getBoolean("web-editor.enable")) {
                UltiTools.getInstance().getLogger().log(Level.INFO, UltiTools.getInstance().i18n("网页编辑器已启动！访问地址：https://panel.ultikits.com/manger"));
            } else {
                UltiTools.getInstance().getLogger().log(Level.INFO, UltiTools.getInstance().i18n("网页编辑器未启用！"));
            }
        });
    }

    @NotNull
    private static WebsocketClient getPanelWebsocketClient() throws URISyntaxException {
        WebsocketClient panelWS = new WebsocketClient("https://ws.ultikits.com", CommonUtils.getUltiToolsUUID(), token.getAccess_token());
        panelWS.connect(client -> client.getSocket().on("update_config", args -> {
            Ack ack = (Ack) args[args.length - 1];
            JSONObject jsonObject = JSONObject.parseObject(args[0].toString());
            try {
                ConfigEditorUtils.updateConfigMap(jsonObject.getString("data"));
                ack.call("ok");
            } catch (IOException e) {
                ack.call("error");
            }
        }));
        return panelWS;
    }

    private static void printLoadingBar(final int percentage) {
        StringBuilder loadingBar = new StringBuilder("[");
        int progress = percentage / 10;
        for (int i = 0; i < progress; i++) {
            loadingBar.append("*");
        }
        for (int i = progress; i < 10; i++) {
            loadingBar.append("-");
        }
        loadingBar.append("] ");
        loadingBar.append(percentage);
        loadingBar.append("%");
        Bukkit.getLogger().log(Level.INFO, "[UltiTools]Downloading: " + loadingBar);
    }
}
