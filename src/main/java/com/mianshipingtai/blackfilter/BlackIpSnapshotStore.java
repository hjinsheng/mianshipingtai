package com.mianshipingtai.blackfilter;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 黑名单本地快照存储。
 * <p>
 * 用于 Nacos 不可用时（尤其是应用重启场景）从本地恢复最后一次成功加载的黑名单。
 * 所有方法都不向外抛异常：快照只是兜底手段，不能影响主流程。
 */
@Slf4j
public final class BlackIpSnapshotStore {

    private BlackIpSnapshotStore() {
    }

    /**
     * 读取本地快照
     *
     * @param snapshotPath 快照路径，支持相对路径（相对于应用运行目录）
     * @return 快照内容；文件不存在或读取失败返回 null
     */
    public static String load(String snapshotPath) {
        if (snapshotPath == null || snapshotPath.trim().isEmpty()) {
            return null;
        }
        Path path = Paths.get(snapshotPath).toAbsolutePath();
        if (!Files.isRegularFile(path)) {
            log.warn("未找到黑名单本地快照：{}", path);
            return null;
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            log.info("已读取黑名单本地快照：{}", path);
            return content;
        } catch (Exception e) {
            log.warn("读取黑名单本地快照失败，忽略快照：{}", path, e);
            return null;
        }
    }

    /**
     * 保存本地快照。
     * 先写临时文件再原子替换，避免进程崩溃时留下半个文件。
     *
     * @param snapshotPath 快照路径，支持相对路径（相对于应用运行目录）
     * @param content      快照内容
     * @return 是否保存成功；失败只记录日志
     */
    public static boolean save(String snapshotPath, String content) {
        if (snapshotPath == null || snapshotPath.trim().isEmpty() || content == null) {
            return false;
        }
        Path path = Paths.get(snapshotPath).toAbsolutePath();
        Path tempPath = null;
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            tempPath = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
            Files.writeString(tempPath, content, StandardCharsets.UTF_8);
            try {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("黑名单本地快照已保存：{}", path);
            return true;
        } catch (Exception e) {
            log.warn("保存黑名单本地快照失败：{}", path, e);
            return false;
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (Exception ignored) {
                    // 临时文件清理失败不影响主流程
                }
            }
        }
    }
}
