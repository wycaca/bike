package cn.bike.platform.ops;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationsEvidenceStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void 应保存真实图片并生成内容摘要() throws Exception {
        var storage = new OperationsEvidenceStorage(tempDir.toString());
        var png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        var file = new MockMultipartFile("file", "现场照片.png", "image/png", png);

        var saved = storage.save(file);

        assertThat(saved.sha256()).hasSize(64);
        assertThat(saved.sizeBytes()).isEqualTo(png.length);
        assertThat(Files.isRegularFile(storage.resolve(saved.storedName()))).isTrue();
    }

    @Test
    void 应拒绝伪装成图片的文本() {
        var storage = new OperationsEvidenceStorage(tempDir.toString());
        var file = new MockMultipartFile("file", "fake.png", "image/png", "not-image".getBytes());

        assertThatThrownBy(() -> storage.save(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("有效图片");
    }
}
