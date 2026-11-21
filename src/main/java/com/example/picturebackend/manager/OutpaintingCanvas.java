package com.example.picturebackend.manager;

import com.example.picturebackend.exception.BusinessException;
import com.example.picturebackend.exception.ErrorCode;
import com.example.picturebackend.exception.ThrowUtils;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** A transparent border marks the area the image model may generate. */
public class OutpaintingCanvas {
    private final byte[] image;
    private final byte[] mask;

    private OutpaintingCanvas(byte[] image, byte[] mask) {
        this.image = image;
        this.mask = mask;
    }

    public byte[] getImage() {
        return image;
    }

    public byte[] getMask() {
        return mask;
    }

    public static OutpaintingCanvas from(byte[] source, double scale) {
        ThrowUtils.throwIf(!Double.isFinite(scale) || scale < 1.1 || scale > 2.0,
                ErrorCode.PARAMS_ERROR, "扩图倍数须在 1.1 到 2.0 之间");
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(source));
            ThrowUtils.throwIf(original == null, ErrorCode.PARAMS_ERROR, "无法读取原图");
            int width = (int) Math.ceil(original.getWidth() * scale);
            int height = (int) Math.ceil(original.getHeight() * scale);
            ThrowUtils.throwIf((long) width * height > 25_000_000L,
                    ErrorCode.PARAMS_ERROR, "扩图画布不能超过 2500 万像素");

            int left = (width - original.getWidth()) / 2;
            int top = (height - original.getHeight()) / 2;
            BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D imageGraphics = canvas.createGraphics();
            imageGraphics.drawImage(original, left, top, null);
            imageGraphics.dispose();

            BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D maskGraphics = mask.createGraphics();
            maskGraphics.setColor(java.awt.Color.WHITE);
            maskGraphics.fillRect(left, top, original.getWidth(), original.getHeight());
            maskGraphics.dispose();

            return new OutpaintingCanvas(png(canvas), png(mask));
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "扩图画布生成失败");
        }
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("PNG encoder unavailable");
        }
        return output.toByteArray();
    }
}
