package tools;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;

import javax.imageio.ImageIO;

/**
 * this class resizes an image retrieved from the phone, and is intended to be
 * used before adding a picture to a database. Thank you very much to
 * codejava.net for the advice on accomplishing this task.
 * 
 * @author Nicholas Caputo, npocaputo@GMail.com, (847) 630 7370
 *
 */
public class Images {

	/**
	 * issues a command to the resize method, and resizes the picture by a
	 * specified percent, to make it proportional
	 * 
	 * @param inputFile
	 * @param output
	 * @param percent
	 * @param width
	 * @param height
	 * @throws IOException
	 */
	public static void resizeProportional(File inputFile, String output, double percent, int width, int height)
			throws IOException {
		log("Resize proportional method started, resizing to " + (percent * 100) + "% current size");
		// File inputFile = new File(stringInput);
		// BufferedImage inputImage = ImageIO.read(inputFile);
		int scaledHeight = (int) (height * percent);
		int scaledWidth = (int) (width * percent);
		resize(inputFile, output, scaledWidth, scaledHeight);
	}

	/**
	 * uses several Java libraries to resize an image and save as a new file
	 * with the specified height and width
	 * 
	 * @param originalPicture
	 * @param output
	 * @param scaledWidth
	 * @param scaledHeight
	 * @throws IOException
	 */
	private static void resize(File originalPicture, String output, int scaledWidth, int scaledHeight)
			throws IOException {
		log("Resizing image to " + scaledWidth + "x" + scaledHeight);
		// read input image
		// File originalPicture = new File(stringInput);
		BufferedImage originalPictureImage = ImageIO.read(originalPicture);

		// create output file
		BufferedImage outputImage = new BufferedImage(scaledWidth, scaledHeight, originalPictureImage.getType());

		// scales output image
		Graphics2D graphics = outputImage.createGraphics();
		graphics.drawImage(originalPictureImage, 0, 0, scaledWidth, scaledHeight, null);
		graphics.dispose();

		// get extension of output file
		String fileExtension = output.substring(output.lastIndexOf(".") + 1);

		log("Writing to file " + output);
		// write to file
		ImageIO.write(outputImage, fileExtension, new File(output));
		log("Wrote to file");
	}

	private static void log(String message) {
		System.out.println('[' + LocalDateTime.now().toString().replace('T', ' ') + ": Images] " + message);
	}
}