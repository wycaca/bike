package cn.bike.platform.report.export;

import cn.bike.platform.report.export.ReportExportModels.StoredFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class ReportFileStorage {

    private final Path root;

    @Autowired
    public ReportFileStorage(@Value("${app.report.storage-path:./build/reports}") String storagePath) {
        this(Path.of(storagePath));
    }

    ReportFileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException exception) {
            throw new IllegalStateException("无法初始化报表存储目录", exception);
        }
    }

    /**
     * 输入: 安全存储键和 CSV 写入函数; 输出: 原子落盘后的文件大小与行数。
     * 先写临时文件再移动，避免 API 进程读取到未完成文件。
     */
    public StoredFile write(String storageKey, ReportFileWriter fileWriter) throws IOException {
        var target = resolve(storageKey);
        var temporary = resolve(storageKey + ".part");
        Files.deleteIfExists(temporary);
        long rowCount;
        try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            rowCount = fileWriter.write(writer);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new StoredFile(storageKey, Files.size(target), rowCount);
    }

    /** 输入: 数据库存储键; 输出: 已存在且位于根目录内的报表路径。 */
    public Path resolveExisting(String storageKey) {
        var path = resolve(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("报表文件不存在或已被清理");
        }
        return path;
    }

    /** 输入: 存储键; 输出: 无, 幂等删除对应文件和临时文件。 */
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return;
        try {
            Files.deleteIfExists(resolve(storageKey));
            Files.deleteIfExists(resolve(storageKey + ".part"));
        } catch (IOException exception) {
            throw new IllegalStateException("无法清理过期报表文件", exception);
        }
    }

    /** 输入: 受限字符集的存储键; 输出: 确认未越过根目录的规范化路径。 */
    private Path resolve(String storageKey) {
        if (storageKey == null || !storageKey.matches("^[A-Za-z0-9._-]{1,128}$")) {
            throw new IllegalArgumentException("非法报表存储键");
        }
        var path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("报表路径超出存储目录");
        }
        return path;
    }

    @FunctionalInterface
    public interface ReportFileWriter {
        /** 输入: UTF-8 缓冲字符流; 输出: 写入的数据行数。 */
        long write(BufferedWriter writer) throws IOException;
    }
}
