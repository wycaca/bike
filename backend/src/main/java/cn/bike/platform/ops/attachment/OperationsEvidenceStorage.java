package cn.bike.platform.ops.attachment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Component
@Profile("!report-worker")
public class OperationsEvidenceStorage {

    private static final long MAX_FILE_SIZE = 8L * 1024 * 1024;
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png"
    );

    private final Path root;

    public OperationsEvidenceStorage(@Value("${app.operations.evidence-storage-path}") String storagePath) {
        root = Path.of(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建运维凭证存储目录", exception);
        }
    }

    /** 输入: 用户上传的照片; 输出: 已落盘文件的名称、大小和摘要。 */
    public SavedFile save(MultipartFile file) {
        validate(file);
        var contentType = file.getContentType().toLowerCase(java.util.Locale.ROOT);
        var storedName = UUID.randomUUID() + ALLOWED_TYPES.get(contentType);
        var target = resolve(storedName);
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new SavedFile(storedName, contentType, Files.size(target),
                    HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            deleteQuietly(storedName);
            throw new IllegalStateException("运维凭证保存失败", exception);
        }
    }

    /** 输入: 数据库中保存的相对文件名; 输出: 限定在凭证根目录中的绝对路径。 */
    public Path resolve(String storedName) {
        if (storedName == null || storedName.isBlank() || !Path.of(storedName).getFileName().toString().equals(storedName)) {
            throw new IllegalArgumentException("凭证文件名不合法");
        }
        var resolved = root.resolve(storedName).normalize();
        if (!resolved.getParent().equals(root)) {
            throw new IllegalArgumentException("凭证文件路径不合法");
        }
        return resolved;
    }

    public void deleteQuietly(String storedName) {
        try {
            delete(storedName);
        } catch (RuntimeException ignored) {
            // 数据库写入失败时尽力清理孤立文件, 清理失败不覆盖原始异常.
        }
    }

    /** 输入: 数据库保存的相对文件名; 输出: 文件不存在或删除成功, 失败时抛出异常以便上层重试. */
    public void delete(String storedName) {
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException exception) {
            throw new IllegalStateException("运维凭证文件删除失败", exception);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的凭证照片");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("单张凭证照片不能超过 8 MB");
        }
        var contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.containsKey(contentType.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("凭证仅支持 JPEG 或 PNG 图片");
        }
        try (InputStream input = file.getInputStream()) {
            if (ImageIO.read(input) == null) {
                throw new IllegalArgumentException("上传内容不是有效图片");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取上传图片", exception);
        }
    }

    public record SavedFile(String storedName, String contentType, long sizeBytes, String sha256) {
    }
}
