package org.isf.utils.image;

import java.awt.*;
import java.awt.image.BufferedImage;

public final class ImageUtil {
    private ImageUtil() {
    }

    public static BufferedImage scaleImage(final BufferedImage src,
                                           final int boundWidth,
                                           final int boundHeight){
        final int originalWidth = src.getWidth();
        final int originalHeight = src.getHeight();
        int newWidth = originalWidth;
        int newHeight = originalHeight;

        // first check if we need to scale width
        if (originalWidth > boundWidth) {
            //scale width to fit
            newWidth = boundWidth;
            //scale height to maintain aspect ratio
            newHeight = (newWidth * originalHeight) / originalWidth;
        }

        // then check if we need to scale even with the new height
        if (newHeight > boundHeight) {
            //scale height to fit instead
            newHeight = boundHeight;
            //scale width to maintain aspect ratio
            newWidth = (newHeight * originalWidth) / originalHeight;
        }

        final BufferedImage resizedImg = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g2 = resizedImg.createGraphics();
        g2.setBackground(Color.WHITE);
        g2.clearRect(0,0,newWidth, newHeight);
        g2.drawImage(src, 0, 0, newWidth, newHeight, null);
        g2.dispose();
        return resizedImg;
    }
}
