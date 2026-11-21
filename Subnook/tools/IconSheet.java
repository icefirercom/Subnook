import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * 把预设图标拼成一张总览图，方便人工核对有没有抓错。
 * 用法: java tools/IconSheet.java <输出png> [key1 key2 ...]   不给 key 则输出全部 png/jpg 图标
 * 注：ImageIO 不支持 webp，webp 图标会显示为 "webp"。
 */
public class IconSheet {
    public static void main(String[] args) throws Exception {
        File dir = new File("app/src/main/res/drawable-nodpi");
        List<String> keys = new ArrayList<>();
        if (args.length > 1) keys.addAll(Arrays.asList(args).subList(1, args.length));
        else for (File f : Objects.requireNonNull(dir.listFiles()))
            if (f.getName().startsWith("preset_")) keys.add(f.getName().replaceAll("^preset_|\\.[a-z]+$", ""));
        Collections.sort(keys);
        int cols = 10, cell = 120, icon = 84;
        int rows = (keys.size() + cols - 1) / cols;
        BufferedImage out = new BufferedImage(cols * cell, rows * cell, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(new Color(0xEEF0F5)); g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            int x = (i % cols) * cell + (cell - icon) / 2, y = (i / cols) * cell + 6;
            File f = null;
            for (String ext : new String[]{"png", "jpg", "webp"}) {
                File c = new File(dir, "preset_" + k + "." + ext);
                if (c.exists()) { f = c; break; }
            }
            BufferedImage img = f == null ? null : ImageIO.read(f);
            if (img != null) g.drawImage(img, x, y, icon, icon, null);
            else { g.setColor(Color.LIGHT_GRAY); g.fillRect(x, y, icon, icon); g.setColor(Color.DARK_GRAY);
                   g.drawString(f == null ? "none" : "webp", x + 24, y + 46); }
            g.setColor(Color.BLACK);
            String label = k.length() > 16 ? k.substring(0, 16) : k;
            int w = g.getFontMetrics().stringWidth(label);
            g.drawString(label, (i % cols) * cell + (cell - w) / 2, y + icon + 16);
        }
        ImageIO.write(out, "png", new File(args[0]));
        System.out.println("sheet: " + keys.size() + " icons -> " + args[0]);
    }
}
