import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/** 从截图裁出一块并做圆角透明：java tools/CropRound.java in.png out.png x y w h radius [scaleWidth] */
public class CropRound {
    public static void main(String[] a) throws Exception {
        BufferedImage src = ImageIO.read(new File(a[0]));
        int x = Integer.parseInt(a[2]), y = Integer.parseInt(a[3]), w = Integer.parseInt(a[4]), h = Integer.parseInt(a[5]);
        int r = Integer.parseInt(a[6]);
        int ow = a.length > 7 ? Integer.parseInt(a[7]) : w;
        int oh = h * ow / w;
        BufferedImage out = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setClip(new RoundRectangle2D.Float(0, 0, ow, oh, r * 2f * ow / w, r * 2f * ow / w));
        g.drawImage(src.getSubimage(x, y, w, h), 0, 0, ow, oh, null);
        ImageIO.write(out, "png", new File(a[1]));
    }
}
