package com.howe.ai.storage;

import com.howe.ai.api.AiException;
import com.howe.common.storage.StorageService;
import com.howe.common.utils.ConfigUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 生成图补偿失败与 base64 边界回归。 */
class AiImageTransferTest {
    /** 上传和补偿均失败时，不得返回一个没有实际文件的成功地址。 */
    @Test
    void doesNotReturnSuccessWhenCompensationFails() throws Exception {
        StorageService storage = mock(StorageService.class);
        AiImageCompensationStore compensation = mock(AiImageCompensationStore.class);
        AiImageTransfer transfer = new AiImageTransfer(storage, compensation);
        when(storage.store(anyString(), any(), anyLong(), anyString())).thenThrow(new IllegalStateException("上传失败"));
        doThrow(new IllegalStateException("磁盘已满")).when(compensation).enqueue(anyString(), any(), anyString(), anyInt(), anyString());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", bytes);
        try (MockedStatic<ConfigUtils> config = mockStatic(ConfigUtils.class)) {
            config.when(() -> ConfigUtils.getInt(anyString(), anyInt())).thenAnswer(call -> {
                String key = call.getArgument(0);
                return key.endsWith("retryMax") ? 0 : call.getArgument(1);
            });
            config.when(() -> ConfigUtils.getBoolean(anyString(), anyBoolean())).thenReturn(true);
            assertThrows(AiException.class, () -> transfer.transfer(null, Base64.getEncoder().encodeToString(bytes.toByteArray()), "image/png"));
        }
        verify(storage, never()).urlOf(anyString());
    }

    /** 过大 base64 在解码前拒绝，不把完整内容再分配为字节数组。 */
    @Test
    void rejectsOversizedBase64() {
        AiImageTransfer transfer = new AiImageTransfer(mock(StorageService.class), mock(AiImageCompensationStore.class));
        String content = "A".repeat(4 * ((AiImageContentGuard.MAX_BYTES + 2) / 3) + 4);
        assertThrows(AiException.class, () -> ReflectionTestUtils.invokeMethod(transfer, "decodeBase64", content));
    }
}
