import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** 截掉手机截图顶部状态栏并缩放：java tools/CropTop.java in.png out.png topPx bottomPx outWidth */
public class CropTop {
    public static void main(String[] a) throws Exception {
        BufferedImage src = ImageIO.read(new File(a[0]));
        int top = Integer.parseInt(a[2]), bottom = Integer.parseInt(a[3]), ow = Integer.parseInt(a[4]);
        int h = src.getHeight() - top - bottom;
        int oh = h * ow / src.getWidth();
        BufferedImage out = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src.getSubimage(0, top, src.getWidth(), h), 0, 0, ow, oh, null);
        ImageIO.write(out, "png", new File(a[1]));
    }
}
