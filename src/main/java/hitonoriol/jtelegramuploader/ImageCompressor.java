package hitonoriol.jtelegramuploader;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

public class ImageCompressor {
	public static final float DEFAULT_SCALE_FACTOR = 0.85f, DEFAULT_COMPRESSION_LEVEL = 0.45f;
	private float scaleFactor = DEFAULT_SCALE_FACTOR, compressionQuality = DEFAULT_COMPRESSION_LEVEL;

	public void setScaleFactor(float scaleFactor) {
		this.scaleFactor = scaleFactor;
	}

	public void setCompressionQuality(float compressionQuality) {
		this.compressionQuality = compressionQuality;
	}

	public static String getImageType(File imageFile) {
		String format = null;
		try (InputStream is = new FileInputStream(imageFile);
				ImageInputStream iis = ImageIO.createImageInputStream(is)) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
			if (readers.hasNext())
				format = readers.next().getFormatName();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return format;
	}

	public BufferedImage scaleImage(BufferedImage src) {
		BufferedImage image = new BufferedImage(scale(src.getWidth()), scale(src.getHeight()),
				BufferedImage.TYPE_INT_ARGB);

		AffineTransform at = new AffineTransform();
		at.scale(scaleFactor, scaleFactor);
		AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
		return scaleOp.filter(src, image);
	}

	public File compressImageFile(File srcImage) {
		File compressed = new File(srcImage.getParent() + File.separator + "tmp-" + srcImage.getName());
		try (OutputStream os = new FileOutputStream(compressed);
				ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
			Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(getImageType(srcImage));
			ImageWriter writer = writers.next();
			writer.setOutput(ios);
			writer.write(null,
					new IIOImage(scaleImage(ImageIO.read(srcImage)), null, null),
					setUpCompression(writer));
			writer.dispose();
			compressed.deleteOnExit();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return compressed;
	}

	private ImageWriteParam setUpCompression(ImageWriter writer) {
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(compressionQuality);
		return param;
	}

	public int scale(int dimension) {
		return (int) (dimension * scaleFactor);
	}
}
