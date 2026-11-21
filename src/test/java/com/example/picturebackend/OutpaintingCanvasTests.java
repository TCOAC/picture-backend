package com.example.picturebackend;

import com.example.picturebackend.exception.BusinessException;
import com.example.picturebackend.manager.OutpaintingCanvas;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class OutpaintingCanvasTests {
    @Test
    void preservesOriginalPixelsAndMasksOnlyTheBorder() throws Exception {
        BufferedImage original = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 4; x++) {
                original.setRGB(x, y, Color.RED.getRGB());
            }
        }
        ByteArrayOutputStream source = new ByteArrayOutputStream();
        ImageIO.write(original, "png", source);

        OutpaintingCanvas result = OutpaintingCanvas.from(source.toByteArray(), 2.0);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result.getImage()));
        BufferedImage mask = ImageIO.read(new ByteArrayInputStream(result.getMask()));
        assertEquals(8, image.getWidth());
        assertEquals(4, image.getHeight());
        assertEquals(0, image.getRGB(0, 0) >>> 24);
        assertEquals(Color.RED.getRGB(), image.getRGB(2, 1));
        assertEquals(0, mask.getRGB(0, 0) >>> 24);
        assertEquals(Color.WHITE.getRGB(), mask.getRGB(2, 1));
    }

    @Test
    void rejectsUnsupportedScale() {
        assertThrows(BusinessException.class, () -> OutpaintingCanvas.from(new byte[0], 3.0));
    }
}
