package ba.tfb.tasknest.photo;

import ba.tfb.tasknest.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

@Component
public class PhotoProcessor {

    static final int MAX_SOURCE_PIXELS = 40_000_000;
    static final int MAX_EDGE = 1920;
    private static final float JPEG_QUALITY = 0.85f;

    public ProcessedPhoto process(byte[] bytes) {
        PhotoFormat format = PhotoFormat.detect(bytes)
                .orElseThrow(() -> new BusinessRuleException("Only JPEG and PNG photos are accepted"));

        BufferedImage source = read(bytes);
        BufferedImage scaled = scaleDown(source, format);
        return new ProcessedPhoto(write(scaled, format), format, scaled.getWidth(), scaled.getHeight());
    }

    private BufferedImage read(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw unreadable();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_SOURCE_PIXELS) {
                    throw new BusinessRuleException("The photo has too many pixels; use one under 40 megapixels");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessRuleException rule) {
                throw rule;
            }
            throw unreadable();
        }
    }

    private BufferedImage scaleDown(BufferedImage source, PhotoFormat format) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        double ratio = longest > MAX_EDGE ? (double) MAX_EDGE / longest : 1.0;
        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        int type = format == PhotoFormat.JPEG ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;

        BufferedImage target = new BufferedImage(width, height, type);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] write(BufferedImage image, PhotoFormat format) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format.imageIoName()).next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (format == PhotoFormat.JPEG) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Encoding the photo failed", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static BusinessRuleException unreadable() {
        return new BusinessRuleException("The photo could not be read; it may be damaged");
    }
}
