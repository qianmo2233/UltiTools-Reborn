package com.ultikits.ultitools;

import com.ultikits.ultitools.commands.PluginInstallCommands;
import com.ultikits.ultitools.commands.UltiToolsCommands;
import com.ultikits.ultitools.entities.Language;
import com.ultikits.ultitools.interfaces.DataStore;
import com.ultikits.ultitools.interfaces.Localized;
import com.ultikits.ultitools.interfaces.VersionWrapper;
import com.ultikits.ultitools.interfaces.impl.data.mysql.MysqlDataStore;
import com.ultikits.ultitools.listeners.PlayerJoinListener;
import com.ultikits.ultitools.manager.*;
import com.ultikits.ultitools.utils.Metrics;
import com.ultikits.ultitools.utils.PluginInitiationUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import me.tongfei.progressbar.DelegatingProgressBarConsumer;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.ultikits.ultitools.utils.PluginInitiationUtils.*;

/**
 * UltiTools plugin main class.
 * <p>
 * UltiTools插件主类。
 *
 * @author wisdommen, qianmo
 * @version 6.0.7
 */
public final class UltiTools extends JavaPlugin implements Localized {
    private boolean needLoadLib = false;
    private static UltiTools ultiTools;
    @Getter
    private final ListenerManager listenerManager = new ListenerManager();
    @Getter
    private final CommandManager commandManager = new CommandManager();
    @Getter
    private DependenceManagers dependenceManagers;
    @Getter
    private VersionWrapper versionWrapper;
    @Getter
    private Language language;
    @Getter
    private PluginManager pluginManager;
    @Getter
    private ConfigManager configManager;
    @Getter
    @Setter
    private DataStore dataStore;

    /**
     * Returns the instance of the UltiTools.
     * <p>
     * 获取UltiTools的实例。
     *
     * @return the instance of the UltiTools <br> UltiTools的实例
     */
    public static UltiTools getInstance() {
        return ultiTools;
    }

    /**
     * Gets the version of UltiTools.
     * <p>
     * 获取UltiTools的版本。
     *
     * @return the version of the UltiTools <br> UltiTools的版本
     */
    public static int getPluginVersion() {
        String versionString = getEnv().getString("version");
        if (versionString == null) {
            throw new RuntimeException("Version not found in env.yml!");
        }
        return Integer.parseInt(versionString.replace(".", ""));
    }

    /**
     * Retrieves the YAML configuration object containing environment variables.
     * <p>
     * 获取包含环境变量的YAML配置对象。
     *
     * @return the YAML configuration object <br> YAML配置对象
     */
    public static YamlConfiguration getEnv() {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(Objects.requireNonNull(getInstance().getTextResource("env.yml")));
        } catch (IOException | InvalidConfigurationException e) {
            throw new RuntimeException(e);
        }
        return config;
    }

    @Override
    public void onLoad() {
        runTestProgressBar();
        ultiTools = this;
        printBanner();
        saveDefaultConfig();
        // Plugin classloader initialization
        URL serverJar = getServerJar();
        try {
            if (serverJar != null) {
                File serverFile = new File(serverJar.toURI());
                String name = serverFile.getName().split("\\.jar")[0];
                getLogger().info("Server Jar detected: " + name);
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        needLoadLib = downloadRequiredDependencies();
    }

    @SneakyThrows
    @Override
    public void onEnable() {
        // Load all lib
        URLClassLoader urlClassLoader = new URLClassLoader(PluginInitiationUtils.getLibs(), getClassLoader());
        // External bukkit libraries initialization
        try {
            dependenceManagers = new DependenceManagers(this, urlClassLoader);
        } catch (Exception e) {
            needLoadLib = true;
        }
        if (needLoadLib) {
            getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> getLogger().log(Level.WARNING, "UltiTools初始化完成，但是无法加载依赖，请重启服务端！"), 0, 20 * 30);
            return;
        }
        // Language initialization
        String lanPath = "lang/" + getConfig().getString("language") + ".json";
        InputStream in = getFileResource(lanPath);
        @SuppressWarnings("DataFlowIssue")
        String result = new BufferedReader(new InputStreamReader(in)).lines().collect(Collectors.joining(""));
        this.language = new Language(result);

        // Adopt server version
        this.versionWrapper = new SpigotVersionManager().match();
        if (this.versionWrapper == null) {
            Bukkit.getLogger().log(
                    Level.SEVERE,
                    "[UltiTools-API] Your server version isn't supported in UltiTools-API!"
            );
            return;
        }

        // Config initialization & DataStore initialization
        configManager = new ConfigManager();
        if (getConfig().getBoolean("mysql.enable")) {
            MysqlDataStore mysqlDataStore = new MysqlDataStore();
            if (mysqlDataStore.getDataSource() != null) {
                DataStoreManager.register(mysqlDataStore);
            }
        }
        String storeType = getConfig().getString("datasource.type");
        //noinspection DataFlowIssue
        dataStore = DataStoreManager.getDatastore(storeType);
        if (dataStore == null) {
            dataStore = DataStoreManager.getDatastore("json");
        }

        // initialize plugin modules
        pluginManager = new PluginManager();
        File file = new File(getDataFolder() + File.separator + "plugins");
        if (!file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.mkdirs();
        }
        try {
            pluginManager.init(urlClassLoader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        @SuppressWarnings("unused")
        Metrics metrics = new Metrics(this, 8652);

        // Embed web server initialization & Account login
        String username = UltiTools.getInstance().getConfig().getString("account.username");
        String password = UltiTools.getInstance().getConfig().getString("account.password");
        boolean loginRequired = username != null && password != null && !username.isEmpty() && !password.isEmpty();
        boolean loginSuccess = false;
        try {
            if (loginRequired) {
                loginSuccess = loginAccount(username, password);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (loginSuccess && getConfig().getBoolean("web-editor.enable")) {
            getLogger().log(Level.INFO, i18n("正在初始化配置编辑Websocket服务..."));
            PluginInitiationUtils.initWebsocket();
        }

        Bukkit.getServicesManager().register(
                PluginManager.class,
                this.pluginManager,
                this,
                ServicePriority.Normal
        );

        //noinspection deprecation
        getCommandManager().register(new UltiToolsCommands());
        //noinspection deprecation
        getCommandManager().register(new PluginInstallCommands());

        Bukkit.getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);

        getLogger().log(Level.INFO, String.format(i18n("数据存储方式：%s"), dataStore.getStoreType()));

        checkAccountLogin(loginRequired, loginSuccess, username);
        checkPluginVersion();
    }

    @Override
    public void onDisable() {
        if (needLoadLib) {
            return;
        }
        // Plugin shutdown logic
        dependenceManagers.closeAdventure();
        stopWebsocket();
        pluginManager.close();
        dependenceManagers.closeSpringContext();
        getCommandManager().close();
        DataStoreManager.close();
        getConfigManager().saveAll();
        Bukkit.getServicesManager().unregisterAll(this);
    }

    /**
     * Reloads the UltiTools plugins by calling the reload method in the PluginManager.
     * <p>
     * 通过调用PluginManager中的reload方法重新加载UltiTools插件。
     *
     * @throws IOException if an I/O error occurs during the reloading process
     */
    public void reloadPlugins() throws IOException {
        pluginManager.reload();
    }

    /**
     * Returns the supported language codes.
     * <p>
     * 返回支持的语言代码。
     *
     * @return a list of supported language codes <br> 支持的语言代码列表
     */
    @Override
    public List<String> supported() {
        return Arrays.asList("en", "zh");
    }

    /**
     * Internationalization method that translates the given string based on the current language.
     * If the string is not found in the dictionary, the original string is returned.
     * <p>
     * 根据当前语言翻译给定的字符串的国际化方法。
     * 如果在字典中找不到字符串，则返回原始字符串。
     *
     * @param str the string to be translated <br> 要翻译的字符串
     * @return the translated string or the original string if not found in the dictionary <br> 翻译后的字符串，如果在字典中找不到，则为原始字符串
     */
    public String i18n(String str) {
        return this.language.getLocalizedText(str);
    }

    /**
     * Retrieves the input stream for the specified file resource.
     * <p>
     * 获取指定文件资源的输入流。
     *
     * @param filename the name of the file resource
     * @return the input stream for the file resource, or null if an I/O error occurs
     */
    public InputStream getFileResource(String filename) {
        try {
            return Objects.requireNonNull(this.getClass().getClassLoader().getResource(filename)).openStream();
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * Get the economy provider
     * <p>
     * 获取经济服务提供者
     *
     * @return the instance of the Economy provider <br> 经济服务提供者实例
     */
    public Economy getEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            throw new RuntimeException("Vault not found!");
        }
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            throw new RuntimeException("Economy service not found!");
        }
        return registration.getProvider();
    }

    private void runTestProgressBar() {
        DelegatingProgressBarConsumer progressBarConsumer =
                new DelegatingProgressBarConsumer(System.console() != null ? System.console().writer()::print : this.getLogger()::info);
        try(ProgressBar pb = new ProgressBarBuilder()
                .setInitialMax(100)
                .setTaskName("Test")
                .setConsumer(progressBarConsumer)
                .setUpdateIntervalMillis(10)
                .build()) {
            for (int i = 0; i < 100; i++) {

                if (System.console() != null) {
                    pb.step();
                    System.console().flush();
                    Thread.sleep(1000);
                    System.console().writer().print("\033[K\r");
                    System.console().flush();
                } else {
                    pb.step();
                    Thread.sleep(1000);
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
