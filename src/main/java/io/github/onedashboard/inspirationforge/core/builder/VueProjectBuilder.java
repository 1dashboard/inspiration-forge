package io.github.onedashboard.inspirationforge.core.builder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Builds generated Vue projects and returns user-facing diagnostics. */
@Slf4j
@Component
public class VueProjectBuilder {

    private static final int MAX_OUTPUT_CHARS = 60_000;

    public record BuildResult(boolean success, String stage, String output, long durationMs) {
    }

    private record CommandResult(boolean success, String output) {
    }

    public void buildProjectAsync(String projectPath) {
        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis())
                .start(() -> {
                    try {
                        buildProject(projectPath);
                    } catch (Exception e) {
                        log.error("异步构建 Vue 项目时发生异常: {}", e.getMessage(), e);
                    }
                });
    }

    /** Retains the previous boolean API for deployment and generation callers. */
    public boolean buildProject(String projectPath) {
        return checkProject(projectPath).success();
    }

    public BuildResult checkProject(String projectPath) {
        Instant startedAt = Instant.now();
        File projectDir = new File(projectPath);
        if (!projectDir.isDirectory()) {
            return result(false, "PROJECT", "项目目录不存在：" + projectPath, startedAt);
        }
        if (!new File(projectDir, "package.json").isFile()) {
            return result(false, "PROJECT", "项目目录中缺少 package.json", startedAt);
        }

        log.info("开始检查 Vue 项目构建：{}", projectPath);
        ensureRelativeViteBase(projectDir);
        List<String> output = new ArrayList<>();
        CommandResult install = executeCommand(projectDir,
                List.of(npmCommand(), "install", "--no-audit", "--no-fund"), 300);
        output.add("$ npm install --no-audit --no-fund\n" + install.output());
        if (!install.success()) {
            return result(false, "DEPENDENCIES", String.join("\n\n", output), startedAt);
        }

        CommandResult build = executeCommand(projectDir,
                List.of(npmCommand(), "run", "build", "--", "--base=./"), 180);
        output.add("$ npm run build -- --base=./\n" + build.output());
        if (!build.success()) {
            return result(false, "BUILD", String.join("\n\n", output), startedAt);
        }
        if (!new File(projectDir, "dist").isDirectory()) {
            output.add("构建命令执行成功，但没有生成 dist 目录。");
            return result(false, "OUTPUT", String.join("\n\n", output), startedAt);
        }
        output.add("构建成功，dist 目录已生成。");
        return result(true, "COMPLETED", String.join("\n\n", output), startedAt);
    }

    private void ensureRelativeViteBase(File projectDir) {
        String[] configNames = {"vite.config.js", "vite.config.ts", "vite.config.mjs", "vite.config.cjs"};
        for (String configName : configNames) {
            Path configPath = projectDir.toPath().resolve(configName);
            if (!Files.isRegularFile(configPath)) {
                continue;
            }
            try {
                String content = Files.readString(configPath, StandardCharsets.UTF_8);
                if (content.matches("(?s).*\\bbase\\s*:\\s*['\\\"]\\.?/['\\\"].*")) {
                    return;
                }
                int configStart = content.indexOf("defineConfig({");
                if (configStart < 0) {
                    return;
                }
                int insertAt = configStart + "defineConfig({".length();
                String updated = content.substring(0, insertAt)
                        + "\n  base: './',"
                        + content.substring(insertAt);
                Files.writeString(configPath, updated, StandardCharsets.UTF_8);
                log.info("已为生成项目补充 Vite 相对资源路径: {}", configPath);
            } catch (Exception e) {
                log.warn("无法修正 Vite base 配置: {}", configPath, e);
            }
            return;
        }
    }

    private BuildResult result(boolean success, String stage, String output, Instant startedAt) {
        return new BuildResult(success, stage, limitOutput(output),
                Duration.between(startedAt, Instant.now()).toMillis());
    }

    private CommandResult executeCommand(File workingDir, List<String> command, int timeoutSeconds) {
        StringBuilder output = new StringBuilder();
        Process process = null;
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), String.join(" ", command));
            process = new ProcessBuilder(command)
                    .directory(workingDir)
                    .redirectErrorStream(true)
                    .start();
            Process runningProcess = process;
            Thread outputReader = Thread.ofVirtual().start(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        runningProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        appendBounded(output, line + System.lineSeparator());
                    }
                } catch (Exception e) {
                    appendBounded(output, "读取命令输出失败：" + e.getMessage());
                }
            });
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                outputReader.join(2_000);
                appendBounded(output, "\n命令执行超时（" + timeoutSeconds + " 秒），已终止。\n");
                return new CommandResult(false, limitOutput(output.toString()));
            }
            outputReader.join(2_000);
            int exitCode = process.exitValue();
            appendBounded(output, "\n进程退出码：" + exitCode + "\n");
            return new CommandResult(exitCode == 0, limitOutput(output.toString()));
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            appendBounded(output, "命令执行失败：" + e.getMessage());
            return new CommandResult(false, limitOutput(output.toString()));
        }
    }

    private synchronized void appendBounded(StringBuilder output, String value) {
        output.append(value);
        if (output.length() > MAX_OUTPUT_CHARS) {
            output.delete(0, output.length() - MAX_OUTPUT_CHARS);
        }
    }

    private String limitOutput(String output) {
        if (output == null || output.length() <= MAX_OUTPUT_CHARS) return output;
        return "...省略较早的构建输出...\n" + output.substring(output.length() - MAX_OUTPUT_CHARS);
    }

    private String npmCommand() {
        return System.getProperty("os.name").toLowerCase().contains("windows") ? "npm.cmd" : "npm";
    }
}
